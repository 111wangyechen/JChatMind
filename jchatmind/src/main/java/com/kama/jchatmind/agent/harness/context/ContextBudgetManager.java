package com.kama.jchatmind.agent.harness.context;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

/**
 * Token 预算管理器
 * 负责估算消息列表的 token 数量，并根据预算阈值判断当前状态
 */
@Slf4j
public class ContextBudgetManager {

    private final int maxTokenBudget;
    private final double warningThreshold;
    private final double criticalThreshold;

    /**
     * 预算状态枚举
     */
    public enum BudgetStatus {
        /** 健康：使用率 < warningThreshold */
        HEALTHY,
        /** 警告：使用率 >= warningThreshold 且 < criticalThreshold */
        WARNING,
        /** 临界：使用率 >= criticalThreshold 且 < 0.9 */
        CRITICAL,
        /** 超出：使用率 >= 0.9 */
        EXCEEDED
    }

    /**
     * @param maxTokenBudget    最大 Token 预算
     * @param warningThreshold  警告阈值（默认 0.4）
     * @param criticalThreshold 临界阈值（默认 0.7）
     */
    public ContextBudgetManager(int maxTokenBudget, double warningThreshold, double criticalThreshold) {
        this.maxTokenBudget = maxTokenBudget;
        this.warningThreshold = warningThreshold;
        this.criticalThreshold = criticalThreshold;
    }

    /**
     * 估算消息列表的 token 数量
     * 中文字符约 1.5 字符/token，英文字符约 4 字符/token
     * 简单近似：totalChars / 2
     *
     * @param messages 消息列表
     * @return 估算的 token 数
     */
    public int estimateTokenCount(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }

        int totalChars = 0;
        for (Message message : messages) {
            String text = extractText(message);
            if (text != null) {
                totalChars += text.length();
            }
        }

        return totalChars / 2;
    }

    /**
     * 检查预算状态
     *
     * @param messages 消息列表
     * @return 当前预算状态
     */
    public BudgetStatus checkBudget(List<Message> messages) {
        int estimatedTokens = estimateTokenCount(messages);
        double usageRatio = (double) estimatedTokens / maxTokenBudget;

        if (usageRatio >= 0.9) {
            return BudgetStatus.EXCEEDED;
        } else if (usageRatio >= criticalThreshold) {
            return BudgetStatus.CRITICAL;
        } else if (usageRatio >= warningThreshold) {
            return BudgetStatus.WARNING;
        } else {
            return BudgetStatus.HEALTHY;
        }
    }

    /**
     * 获取当前使用率百分比
     *
     * @param messages 消息列表
     * @return 使用率百分比（0-100+）
     */
    public double getUsagePercentage(List<Message> messages) {
        int estimatedTokens = estimateTokenCount(messages);
        return (double) estimatedTokens / maxTokenBudget * 100.0;
    }

    /**
     * 获取使用报告字符串（用于日志/SSE）
     *
     * @param messages 消息列表
     * @return 使用报告
     */
    public String getUsageReport(List<Message> messages) {
        int estimatedTokens = estimateTokenCount(messages);
        double usagePercentage = getUsagePercentage(messages);
        BudgetStatus status = checkBudget(messages);

        return String.format(
                "[Token预算] 估算: %d / %d tokens (%.1f%%), 状态: %s, 消息数: %d",
                estimatedTokens, maxTokenBudget, usagePercentage, status, messages.size()
        );
    }

    /**
     * 从 Message 中提取文本内容
     * 需处理不同 Message 子类型
     */
    private String extractText(Message message) {
        if (message instanceof UserMessage userMessage) {
            return userMessage.getText();
        } else if (message instanceof AssistantMessage assistantMessage) {
            return assistantMessage.getText();
        } else if (message instanceof SystemMessage systemMessage) {
            return systemMessage.getText();
        } else if (message instanceof ToolResponseMessage toolResponseMessage) {
            // 遍历所有 ToolResponse，拼接 responseData
            StringBuilder sb = new StringBuilder();
            for (ToolResponseMessage.ToolResponse toolResponse : toolResponseMessage.getResponses()) {
                String data = toolResponse.responseData();
                if (data != null) {
                    sb.append(data);
                }
            }
            return sb.toString();
        }
        // 未知类型，尝试获取文本
        return message.getText();
    }
}
