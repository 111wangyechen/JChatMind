package com.kama.jchatmind.agent.harness.context;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文压缩器
 * 当上下文接近 Token 预算时，按优先级依次压缩消息列表
 */
@Slf4j
public class ContextCompressor {

    private final int toolResultMaxLength;
    private final ContextBudgetManager budgetManager;

    /**
     * @param toolResultMaxLength 工具结果最大长度（超过则截断）
     * @param budgetManager       预算管理器，用于估算 token 数
     */
    public ContextCompressor(int toolResultMaxLength, ContextBudgetManager budgetManager) {
        this.toolResultMaxLength = toolResultMaxLength;
        this.budgetManager = budgetManager;
    }

    /**
     * 压缩消息列表，使其 token 数不超过目标值
     * 按优先级依次应用以下策略：
     * 1. 截断过长的 ToolResponseMessage
     * 2. 移除早期的工具调用/响应对（保留最近 3 轮）
     * 3. 将最早的非系统消息替换为摘要
     *
     * @param messages         原始消息列表（不会被修改）
     * @param targetTokenCount 目标 token 数
     * @return 压缩后的新消息列表
     */
    public List<Message> compress(List<Message> messages, int targetTokenCount) {
        List<Message> result = new ArrayList<>(messages);

        // 策略1：截断过长的 ToolResponseMessage
        result = truncateToolResponses(result);
        int currentTokens = budgetManager.estimateTokenCount(result);
        log.info("[上下文压缩] 截断工具响应后，估算 token 数: {}", currentTokens);
        if (currentTokens <= targetTokenCount) {
            return result;
        }

        // 策略2：移除早期的工具调用/响应对
        result = removeOldToolPairs(result);
        currentTokens = budgetManager.estimateTokenCount(result);
        log.info("[上下文压缩] 移除旧工具对后，估算 token 数: {}", currentTokens);
        if (currentTokens <= targetTokenCount) {
            return result;
        }

        // 策略3：将最早的非系统消息替换为摘要
        result = summarizeEarlyMessages(result);
        currentTokens = budgetManager.estimateTokenCount(result);
        log.info("[上下文压缩] 摘要早期消息后，估算 token 数: {}", currentTokens);

        return result;
    }

    /**
     * 策略1：截断超过 toolResultMaxLength 的 ToolResponseMessage
     */
    private List<Message> truncateToolResponses(List<Message> messages) {
        List<Message> result = new ArrayList<>();
        for (Message message : messages) {
            if (message instanceof ToolResponseMessage toolResponseMessage) {
                List<ToolResponseMessage.ToolResponse> truncatedResponses = new ArrayList<>();
                boolean truncated = false;
                for (ToolResponseMessage.ToolResponse toolResponse : toolResponseMessage.getResponses()) {
                    String data = toolResponse.responseData();
                    if (data != null && data.length() > toolResultMaxLength) {
                        String truncatedData = data.substring(0, toolResultMaxLength) + "...[已截断]";
                        truncatedResponses.add(new ToolResponseMessage.ToolResponse(
                                toolResponse.id(),
                                toolResponse.name(),
                                truncatedData
                        ));
                        truncated = true;
                    } else {
                        truncatedResponses.add(toolResponse);
                    }
                }
                if (truncated) {
                    log.debug("[上下文压缩] 截断了一条 ToolResponseMessage");
                    result.add(ToolResponseMessage.builder()
                            .responses(truncatedResponses)
                            .build());
                } else {
                    result.add(message);
                }
            } else {
                result.add(message);
            }
        }
        return result;
    }

    /**
     * 策略2：移除早期的工具调用/响应对（保留最近 3 轮）
     * 工具调用对 = AssistantMessage(含toolCalls) + 紧随其后的 ToolResponseMessage
     */
    private List<Message> removeOldToolPairs(List<Message> messages) {
        // 找出所有工具调用对的起始索引
        List<Integer> toolPairStartIndices = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            if (msg instanceof AssistantMessage assistantMsg
                    && assistantMsg.getToolCalls() != null
                    && !assistantMsg.getToolCalls().isEmpty()) {
                toolPairStartIndices.add(i);
            }
        }

        // 如果工具调用对不超过 3 个，无需移除
        if (toolPairStartIndices.size() <= 3) {
            return new ArrayList<>(messages);
        }

        // 需要移除的工具对数量
        int toRemove = toolPairStartIndices.size() - 3;
        // 收集需要移除的索引
        java.util.Set<Integer> removeIndices = new java.util.HashSet<>();
        for (int i = 0; i < toRemove; i++) {
            int pairStart = toolPairStartIndices.get(i);
            removeIndices.add(pairStart); // AssistantMessage
            // 紧随其后的 ToolResponseMessage
            if (pairStart + 1 < messages.size()
                    && messages.get(pairStart + 1) instanceof ToolResponseMessage) {
                removeIndices.add(pairStart + 1);
            }
        }

        List<Message> result = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            if (!removeIndices.contains(i)) {
                result.add(messages.get(i));
            }
        }

        log.info("[上下文压缩] 移除了 {} 组早期工具调用/响应对", toRemove);
        return result;
    }

    /**
     * 策略3：将最早的 N 条非系统消息替换为一条摘要 UserMessage
     * 保留 SystemMessage 和最近的消息
     */
    private List<Message> summarizeEarlyMessages(List<Message> messages) {
        // 分离 SystemMessage 和其他消息
        List<Message> systemMessages = new ArrayList<>();
        List<Message> otherMessages = new ArrayList<>();
        for (Message msg : messages) {
            if (msg instanceof SystemMessage) {
                systemMessages.add(msg);
            } else {
                otherMessages.add(msg);
            }
        }

        // 如果非系统消息不超过 4 条，无法进一步压缩
        if (otherMessages.size() <= 4) {
            return new ArrayList<>(messages);
        }

        // 取前半部分进行摘要
        int summarizeCount = otherMessages.size() / 2;
        List<Message> toSummarize = otherMessages.subList(0, summarizeCount);
        List<Message> toKeep = otherMessages.subList(summarizeCount, otherMessages.size());

        // 拼接关键信息生成摘要
        StringBuilder summaryBuilder = new StringBuilder("[对话历史摘要] ");
        for (Message msg : toSummarize) {
            String text = extractMessageText(msg);
            if (text != null && !text.isEmpty()) {
                // 每条消息最多取前 100 个字符
                String prefix = msg instanceof UserMessage ? "用户: " :
                        msg instanceof AssistantMessage ? "助手: " : "工具: ";
                summaryBuilder.append(prefix)
                        .append(text, 0, Math.min(text.length(), 100))
                        .append("; ");
            }
        }

        log.info("[上下文压缩] 将 {} 条早期消息替换为摘要", summarizeCount);

        // 重新组装
        List<Message> result = new ArrayList<>(systemMessages);
        result.add(new UserMessage(summaryBuilder.toString()));
        result.addAll(toKeep);
        return result;
    }

    /**
     * 从消息中提取文本内容
     */
    private String extractMessageText(Message message) {
        if (message instanceof UserMessage userMessage) {
            return userMessage.getText();
        } else if (message instanceof AssistantMessage assistantMessage) {
            return assistantMessage.getText();
        } else if (message instanceof ToolResponseMessage toolResponseMessage) {
            StringBuilder sb = new StringBuilder();
            for (ToolResponseMessage.ToolResponse resp : toolResponseMessage.getResponses()) {
                String data = resp.responseData();
                if (data != null) {
                    sb.append(data);
                }
            }
            return sb.toString();
        }
        return message.getText();
    }
}
