package com.kama.jchatmind.agent.harness.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

/**
 * 对话摘要器
 * 使用 LLM 将长对话压缩为简洁摘要，保留关键信息
 */
@Slf4j
public class ConversationSummarizer {

    private final ChatClient chatClient;

    /**
     * @param chatClient Spring AI ChatClient 实例
     */
    public ConversationSummarizer(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 使用 LLM 生成对话摘要
     *
     * @param messages 消息列表
     * @return 摘要文本
     */
    public String summarize(List<Message> messages) {
        String formattedConversation = formatMessages(messages);

        String promptText = """
                请将以下对话内容压缩为一段简洁的摘要，保留关键信息（用户意图、重要结论、工具调用结果）：
                
                %s
                
                摘要：
                """.formatted(formattedConversation);

        try {
            String summary = chatClient.prompt()
                    .user(promptText)
                    .call()
                    .content();
            log.info("[对话摘要] 成功生成摘要，原始消息数: {}, 摘要长度: {}", messages.size(),
                    summary != null ? summary.length() : 0);
            return summary;
        } catch (Exception e) {
            log.error("[对话摘要] LLM 摘要生成失败: {}", e.getMessage(), e);
            // 降级：返回简单的拼接摘要
            return buildFallbackSummary(messages);
        }
    }

    /**
     * 判断是否需要进行对话摘要
     *
     * @param messageCount       当前消息数量
     * @param estimatedTokenCount 估算的 token 数
     * @param maxBudget          最大预算
     * @return 是否需要摘要
     */
    public boolean shouldSummarize(int messageCount, int estimatedTokenCount, int maxBudget) {
        return messageCount > 10 && estimatedTokenCount > maxBudget * 0.5;
    }

    /**
     * 将消息列表格式化为可读文本
     *
     * @param messages 消息列表
     * @return 格式化后的文本
     */
    private String formatMessages(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (Message message : messages) {
            if (message instanceof SystemMessage systemMessage) {
                sb.append("[系统]: ").append(systemMessage.getText()).append("\n");
            } else if (message instanceof UserMessage userMessage) {
                sb.append("[用户]: ").append(userMessage.getText()).append("\n");
            } else if (message instanceof AssistantMessage assistantMessage) {
                sb.append("[助手]: ").append(assistantMessage.getText());
                if (assistantMessage.getToolCalls() != null && !assistantMessage.getToolCalls().isEmpty()) {
                    sb.append(" (调用了工具: ");
                    for (int i = 0; i < assistantMessage.getToolCalls().size(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(assistantMessage.getToolCalls().get(i).name());
                    }
                    sb.append(")");
                }
                sb.append("\n");
            } else if (message instanceof ToolResponseMessage toolResponseMessage) {
                for (ToolResponseMessage.ToolResponse resp : toolResponseMessage.getResponses()) {
                    sb.append("[工具 ").append(resp.name()).append("]: ");
                    String data = resp.responseData();
                    if (data != null && data.length() > 200) {
                        sb.append(data, 0, 200).append("...");
                    } else {
                        sb.append(data);
                    }
                    sb.append("\n");
                }
            }
        }
        return sb.toString();
    }

    /**
     * 降级摘要：当 LLM 调用失败时，使用简单拼接生成摘要
     */
    private String buildFallbackSummary(List<Message> messages) {
        StringBuilder sb = new StringBuilder("[自动摘要] ");
        int count = 0;
        for (Message message : messages) {
            if (message instanceof UserMessage userMessage) {
                String text = userMessage.getText();
                sb.append("用户: ").append(text, 0, Math.min(text.length(), 80)).append("; ");
                count++;
            } else if (message instanceof AssistantMessage assistantMessage) {
                String text = assistantMessage.getText();
                if (text != null && !text.isEmpty()) {
                    sb.append("助手: ").append(text, 0, Math.min(text.length(), 80)).append("; ");
                    count++;
                }
            }
            if (count >= 5) break; // 降级摘要最多取 5 条
        }
        return sb.toString();
    }
}
