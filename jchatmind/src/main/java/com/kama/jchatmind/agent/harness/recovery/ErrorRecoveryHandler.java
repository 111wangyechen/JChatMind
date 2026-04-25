package com.kama.jchatmind.agent.harness.recovery;

import lombok.extern.slf4j.Slf4j;

/**
 * 错误恢复处理器 — L6 恢复层
 * 针对工具执行失败、LLM 调用失败、状态异常等场景提供恢复建议
 */
@Slf4j
public class ErrorRecoveryHandler {

    private final RetryPolicy retryPolicy;

    public ErrorRecoveryHandler(RetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy;
    }

    // ============================
    //    恢复动作枚举
    // ============================

    /** 建议的恢复动作 */
    public enum RecoveryAction {
        /** 重试当前操作 */
        RETRY,
        /** 跳过当前操作，继续执行 */
        SKIP,
        /** 终止整个流程 */
        ABORT,
        /** 压缩上下文以减少 token 用量 */
        COMPRESS_CONTEXT,
        /** 重置 Agent 状态 */
        RESET_STATE
    }

    // ============================
    //    恢复结果内部类
    // ============================

    /**
     * 恢复结果
     */
    public static class RecoveryResult {
        /** 是否已恢复 */
        private final boolean recovered;
        /** 恢复描述信息 */
        private final String message;
        /** 建议的恢复动作 */
        private final RecoveryAction action;

        public RecoveryResult(boolean recovered, String message, RecoveryAction action) {
            this.recovered = recovered;
            this.message = message;
            this.action = action;
        }

        public boolean isRecovered() {
            return recovered;
        }

        public String getMessage() {
            return message;
        }

        public RecoveryAction getAction() {
            return action;
        }

        @Override
        public String toString() {
            return "RecoveryResult{recovered=" + recovered +
                    ", message='" + message + "'" +
                    ", action=" + action + "}";
        }
    }

    // ============================
    //    工具执行失败恢复
    // ============================

    /**
     * 处理工具执行失败
     * 记录详细错误信息，并生成用户友好的错误描述供 LLM 上下文使用
     *
     * @param exception 工具执行时抛出的异常
     * @param toolName  工具名称
     * @param arguments 工具调用参数
     * @return 恢复结果
     */
    public RecoveryResult handleToolExecutionError(Exception exception, String toolName, String arguments) {
        // 记录详细错误日志
        log.error("[ErrorRecovery] 工具执行失败 — 工具: {}, 参数: {}, 异常: {}",
                toolName, arguments, exception.getMessage(), exception);

        // 判断是否可重试
        if (retryPolicy.shouldRetry(exception, 1)) {
            String message = String.format(
                    "工具 [%s] 执行失败（%s），系统将自动重试。",
                    toolName, exception.getMessage()
            );
            log.info("[ErrorRecovery] 工具执行失败可重试: {}", toolName);
            return new RecoveryResult(true, message, RecoveryAction.RETRY);
        }

        // 不可重试：建议跳过该工具
        String message = String.format(
                "工具 [%s] 执行失败且无法重试（%s）。建议跳过该工具并使用其他方式完成任务。",
                toolName, exception.getMessage()
        );
        log.warn("[ErrorRecovery] 工具执行失败不可重试，建议跳过: {}", toolName);
        return new RecoveryResult(false, message, RecoveryAction.SKIP);
    }

    // ============================
    //    LLM 调用失败恢复
    // ============================

    /**
     * 处理 LLM 调用失败
     * 区分限流、超时、模型不可用、Token 超限等场景，给出对应恢复建议
     *
     * @param exception LLM 调用异常
     * @return 恢复结果
     */
    public RecoveryResult handleLLMError(Exception exception) {
        String errorMessage = exception.getMessage() != null ? exception.getMessage() : "";
        String lowerMessage = errorMessage.toLowerCase();

        log.error("[ErrorRecovery] LLM 调用失败: {}", errorMessage, exception);

        // 场景 1: API 限流（HTTP 429 / Rate Limit）
        if (lowerMessage.contains("429") || lowerMessage.contains("too many requests") ||
            lowerMessage.contains("rate limit")) {
            log.info("[ErrorRecovery] LLM 限流，建议退避重试");
            return new RecoveryResult(true,
                    "LLM API 请求被限流，系统将在短暂等待后自动重试。",
                    RecoveryAction.RETRY);
        }

        // 场景 2: 网络超时
        if (lowerMessage.contains("timeout") || lowerMessage.contains("timed out") ||
            lowerMessage.contains("connection reset") || lowerMessage.contains("connection refused")) {
            log.info("[ErrorRecovery] LLM 网络超时，建议重试");
            return new RecoveryResult(true,
                    "LLM 服务连接超时，系统将自动重试。",
                    RecoveryAction.RETRY);
        }

        // 场景 3: 模型不可用（HTTP 503）
        if (lowerMessage.contains("503") || lowerMessage.contains("service unavailable") ||
            lowerMessage.contains("model not available") || lowerMessage.contains("overloaded")) {
            log.info("[ErrorRecovery] LLM 服务不可用，建议稍后重试");
            return new RecoveryResult(true,
                    "LLM 模型服务暂时不可用，系统将自动重试。",
                    RecoveryAction.RETRY);
        }

        // 场景 4: Token 超限
        if (lowerMessage.contains("token") && (lowerMessage.contains("limit") || lowerMessage.contains("exceed") ||
            lowerMessage.contains("maximum") || lowerMessage.contains("too long"))) {
            log.info("[ErrorRecovery] Token 超限，建议压缩上下文");
            return new RecoveryResult(false,
                    "对话上下文超过模型 Token 限制，建议压缩历史对话或清理不必要的上下文信息。",
                    RecoveryAction.COMPRESS_CONTEXT);
        }

        // 默认：未知 LLM 错误，终止
        log.warn("[ErrorRecovery] 未知 LLM 错误，建议终止当前流程");
        return new RecoveryResult(false,
                "LLM 调用发生未知错误（" + errorMessage + "），无法自动恢复。",
                RecoveryAction.ABORT);
    }

    // ============================
    //    状态异常恢复
    // ============================

    /**
     * 处理 Agent 状态异常
     * 当 Agent 处于非预期状态时，给出恢复建议（重置或标记错误）
     *
     * @param chatSessionId 会话 ID
     * @param currentState  当前异常状态
     * @return 恢复结果
     */
    public RecoveryResult handleStateCorruption(String chatSessionId, String currentState) {
        log.error("[ErrorRecovery] 状态异常 — 会话: {}, 当前状态: {}", chatSessionId, currentState);

        // 如果当前状态已经是 ERROR，建议重置到 IDLE 重新开始
        if ("ERROR".equalsIgnoreCase(currentState)) {
            log.info("[ErrorRecovery] 当前已是 ERROR 状态，建议重置为 IDLE");
            return new RecoveryResult(true,
                    String.format("会话 [%s] 当前处于 ERROR 状态，建议重置为 IDLE 以恢复正常使用。", chatSessionId),
                    RecoveryAction.RESET_STATE);
        }

        // 如果状态为 THINKING 或 EXECUTING，可能是执行中断，建议重置
        if ("THINKING".equalsIgnoreCase(currentState) || "EXECUTING".equalsIgnoreCase(currentState)) {
            log.info("[ErrorRecovery] 状态卡在 {} 阶段，建议重置为 IDLE", currentState);
            return new RecoveryResult(true,
                    String.format("会话 [%s] 状态卡在 %s 阶段（可能因中断导致），建议重置为 IDLE。",
                            chatSessionId, currentState),
                    RecoveryAction.RESET_STATE);
        }

        // 其他异常状态：标记为 ERROR
        log.warn("[ErrorRecovery] 未预期状态 {}，建议标记为 ERROR", currentState);
        return new RecoveryResult(false,
                String.format("会话 [%s] 处于非预期状态 [%s]，已标记为 ERROR 状态。", chatSessionId, currentState),
                RecoveryAction.RESET_STATE);
    }
}
