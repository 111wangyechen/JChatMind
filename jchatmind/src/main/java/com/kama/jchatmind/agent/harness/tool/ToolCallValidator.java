package com.kama.jchatmind.agent.harness.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 工具调用验证器
 * L2 工具系统层组件，负责验证工具调用的合法性
 */
@Slf4j
public class ToolCallValidator {

    /**
     * 验证结果状态
     */
    public enum Status {
        PASS, WARN, REJECT
    }

    /**
     * 验证结果记录
     */
    public record ValidationResult(Status status, String reason, List<String> warnings) {

        /**
         * PASS 或 WARN 都允许执行
         */
        public boolean isAllowed() {
            return status == Status.PASS || status == Status.WARN;
        }

        /**
         * 创建通过的验证结果
         */
        public static ValidationResult pass() {
            return new ValidationResult(Status.PASS, null, List.of());
        }

        /**
         * 创建警告的验证结果
         */
        public static ValidationResult warn(String reason) {
            return new ValidationResult(Status.WARN, reason, List.of(reason));
        }

        /**
         * 创建拒绝的验证结果
         */
        public static ValidationResult reject(String reason) {
            return new ValidationResult(Status.REJECT, reason, List.of());
        }
    }

    /**
     * 批量验证工具调用
     *
     * @param toolCalls      工具调用列表
     * @param availableTools 可用工具列表
     * @return 验证结果
     */
    public ValidationResult validate(List<AssistantMessage.ToolCall> toolCalls,
                                     List<ToolCallback> availableTools) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return ValidationResult.pass();
        }

        List<String> allWarnings = new ArrayList<>();
        for (AssistantMessage.ToolCall toolCall : toolCalls) {
            ValidationResult singleResult = validateSingle(toolCall, availableTools);
            if (singleResult.status() == Status.REJECT) {
                log.warn("工具调用验证被拒绝: toolCall={}, reason={}", toolCall.name(), singleResult.reason());
                return singleResult;
            }
            if (singleResult.status() == Status.WARN) {
                allWarnings.addAll(singleResult.warnings());
            }
        }

        // 检查冲突
        ValidationResult conflictResult = checkConflicts(toolCalls);
        if (conflictResult.status() == Status.REJECT) {
            return conflictResult;
        }
        if (conflictResult.status() == Status.WARN) {
            allWarnings.addAll(conflictResult.warnings());
        }

        if (!allWarnings.isEmpty()) {
            String combinedReason = String.join("; ", allWarnings);
            return new ValidationResult(Status.WARN, combinedReason, allWarnings);
        }

        return ValidationResult.pass();
    }

    /**
     * 验证单个工具调用
     *
     * @param toolCall       工具调用
     * @param availableTools 可用工具列表
     * @return 验证结果
     */
    public ValidationResult validateSingle(AssistantMessage.ToolCall toolCall,
                                           List<ToolCallback> availableTools) {
        // 检查工具名是否在可用工具列表中
        boolean toolExists = availableTools.stream()
                .anyMatch(tc -> tc.getToolDefinition().name().equals(toolCall.name()));
        if (!toolExists) {
            return ValidationResult.reject("未知的工具调用: " + toolCall.name());
        }

        // 检查参数是否为有效 JSON
        String arguments = toolCall.arguments();
        if (arguments != null && !arguments.isBlank()) {
            if (!isValidJson(arguments)) {
                return ValidationResult.reject("工具 " + toolCall.name() + " 的参数不是有效的 JSON: " + arguments);
            }
        }

        return ValidationResult.pass();
    }

    /**
     * 冲突检测
     *
     * @param toolCalls 工具调用列表
     * @return 验证结果
     */
    public ValidationResult checkConflicts(List<AssistantMessage.ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.size() <= 1) {
            return ValidationResult.pass();
        }

        // 检查是否同时调用了 terminate 和其他工具
        boolean hasTerminate = toolCalls.stream()
                .anyMatch(tc -> "terminate".equals(tc.name()));
        if (hasTerminate && toolCalls.size() > 1) {
            return ValidationResult.reject("不允许同时调用 terminate 和其他工具");
        }

        // 检查是否有重复的工具调用
        Set<String> seenTools = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (AssistantMessage.ToolCall toolCall : toolCalls) {
            if (!seenTools.add(toolCall.name())) {
                duplicates.add(toolCall.name());
            }
        }
        if (!duplicates.isEmpty()) {
            return ValidationResult.warn("检测到重复的工具调用: " + duplicates);
        }

        return ValidationResult.pass();
    }

    /**
     * 简单的 JSON 格式检查
     */
    private boolean isValidJson(String str) {
        String trimmed = str.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return true;
        }
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return true;
        }
        return false;
    }
}
