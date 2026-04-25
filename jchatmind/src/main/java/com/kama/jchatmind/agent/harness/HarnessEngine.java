package com.kama.jchatmind.agent.harness;

import com.kama.jchatmind.agent.harness.context.ContextBudgetManager;
import com.kama.jchatmind.agent.harness.context.ContextCompressor;
import com.kama.jchatmind.agent.harness.evaluation.ExecutionMetrics;
import com.kama.jchatmind.agent.harness.evaluation.OutputVerifier;
import com.kama.jchatmind.agent.harness.memory.ConversationSummarizer;
import com.kama.jchatmind.agent.harness.memory.StructuredStateManager;
import com.kama.jchatmind.agent.harness.orchestration.ExecutionTracker;
import com.kama.jchatmind.agent.harness.orchestration.TaskPlanner;
import com.kama.jchatmind.agent.harness.recovery.ErrorRecoveryHandler;
import com.kama.jchatmind.agent.harness.recovery.Guardrails;
import com.kama.jchatmind.agent.harness.recovery.RetryPolicy;
import com.kama.jchatmind.agent.harness.tool.ToolCallValidator;
import com.kama.jchatmind.agent.harness.tool.ToolResultSummarizer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;

/**
 * Harness 中央协调器
 * 串联六层 Harness 能力，在 Agent think-execute 循环的关键节点提供增强
 */
@Slf4j
public class HarnessEngine {

    @Getter
    private final HarnessConfig config;
    private final ChatClient chatClient;

    // L1: 信息边界层
    private final ContextBudgetManager contextBudgetManager;
    private final ContextCompressor contextCompressor;

    // L4: 记忆层
    private final ConversationSummarizer conversationSummarizer;
    private final StructuredStateManager stateManager;

    // L2: 工具系统层
    private final ToolCallValidator toolCallValidator;
    private final ToolResultSummarizer toolResultSummarizer;

    // L3: 执行编排层
    private final TaskPlanner taskPlanner;
    private final ExecutionTracker executionTracker;

    // L5: 评估层
    private final OutputVerifier outputVerifier;
    private final ExecutionMetrics executionMetrics;

    // L6: 恢复层
    private final Guardrails guardrails;
    private final RetryPolicy retryPolicy;
    private final ErrorRecoveryHandler errorRecoveryHandler;

    // 最近一次验证结果（用于 generateCorrectionHint）
    private OutputVerifier.VerificationResult lastVerificationResult;

    public HarnessEngine(HarnessConfig config, ChatClient chatClient) {
        this.config = config;
        this.chatClient = chatClient;

        // L1: 信息边界层
        this.contextBudgetManager = new ContextBudgetManager(
                config.getMaxTokenBudget(),
                config.getContextCompressionThreshold(),
                config.getContextCriticalThreshold()
        );
        this.contextCompressor = new ContextCompressor(
                config.getToolResultMaxLength(),
                this.contextBudgetManager
        );

        // L4: 记忆层
        this.conversationSummarizer = new ConversationSummarizer(chatClient);
        this.stateManager = new StructuredStateManager();

        // L2: 工具系统层
        this.toolCallValidator = new ToolCallValidator();
        this.toolResultSummarizer = new ToolResultSummarizer(config.getToolResultMaxLength());

        // L3: 执行编排层
        this.taskPlanner = new TaskPlanner(chatClient);
        this.executionTracker = new ExecutionTracker();

        // L5: 评估层
        this.outputVerifier = new OutputVerifier();
        this.executionMetrics = new ExecutionMetrics();

        // L6: 恢复层
        this.guardrails = new Guardrails();
        this.retryPolicy = new RetryPolicy(config.getMaxRetries(), config.getRetryBackoffMs());
        this.errorRecoveryHandler = new ErrorRecoveryHandler(this.retryPolicy);

        log.info("[Harness] HarnessEngine initialized with config: {}", config);
    }

    // === 生命周期钩子 ===

    /** Agent run() 开始时调用 */
    public void onRunStart(String chatSessionId) {
        log.info("[Harness] Run started for session: {}", chatSessionId);
        try {
            executionMetrics.reset();
            executionMetrics.startRun();
            executionTracker.reset();
            executionTracker.startRun();
            stateManager.initState("session-" + chatSessionId);
        } catch (Exception e) {
            log.warn("[Harness] onRunStart 降级: {}", e.getMessage());
        }
    }

    /** Agent run() 结束时调用 */
    public void onRunComplete(String chatSessionId) {
        log.info("[Harness] Run completed for session: {}", chatSessionId);
        try {
            executionMetrics.endRun();
        } catch (Exception e) {
            log.warn("[Harness] onRunComplete 降级: {}", e.getMessage());
        }
    }

    // === Think 阶段钩子 ===

    /** think() 之前：L1 上下文预算检查 + 压缩 + L4 状态更新 */
    public List<Message> beforeThink(List<Message> messages, String chatSessionId) {
        log.debug("[Harness] beforeThink - messages count: {}", messages.size());
        try {
            // L1: 检查上下文预算
            ContextBudgetManager.BudgetStatus budgetStatus = contextBudgetManager.checkBudget(messages);
            log.info("[Harness] 上下文预算状态: {}, {}", budgetStatus,
                    contextBudgetManager.getUsageReport(messages));

            List<Message> result = messages;

            // 如果 WARNING 或更高，进行压缩
            if (budgetStatus == ContextBudgetManager.BudgetStatus.WARNING
                    || budgetStatus == ContextBudgetManager.BudgetStatus.CRITICAL
                    || budgetStatus == ContextBudgetManager.BudgetStatus.EXCEEDED) {

                int targetTokens = (int) (config.getMaxTokenBudget() * config.getContextCompressionThreshold());
                result = contextCompressor.compress(messages, targetTokens);
                executionMetrics.recordContextCompression();
                log.info("[Harness] 上下文已压缩，消息数: {} -> {}", messages.size(), result.size());
            }

            // L4: 检查是否需要对话摘要
            int estimatedTokens = contextBudgetManager.estimateTokenCount(result);
            if (conversationSummarizer.shouldSummarize(result.size(), estimatedTokens, config.getMaxTokenBudget())) {
                log.info("[Harness] 触发对话摘要（消息数: {}, tokens: {}）", result.size(), estimatedTokens);
                // 对话摘要已由 ContextCompressor 的策略3处理，此处仅记录
            }

            // L4: 更新结构化状态
            stateManager.updateIntermediateResult("lastBudgetStatus", budgetStatus.name());
            stateManager.updateIntermediateResult("messageCount", String.valueOf(result.size()));

            // 记录 Token 用量
            executionMetrics.recordTokenUsage(estimatedTokens, 0);

            return result;
        } catch (Exception e) {
            log.warn("[Harness] beforeThink 降级: {}", e.getMessage());
            return messages;
        }
    }

    /** think() 之后：L5 输出自验证 */
    public boolean afterThink(AssistantMessage output, List<AssistantMessage.ToolCall> toolCalls,
                              List<Message> conversationContext) {
        log.debug("[Harness] afterThink - toolCalls: {}",
                toolCalls != null ? toolCalls.size() : 0);
        try {
            if (!config.isEnableSelfVerification()) {
                executionMetrics.recordVerification(true);
                return true;
            }

            // 提取最后一条用户消息作为验证参考
            String userMessage = extractLastUserMessage(conversationContext);

            // L5: 调用 OutputVerifier.verify()
            OutputVerifier.VerificationResult result = outputVerifier.verify(output, userMessage, conversationContext);
            this.lastVerificationResult = result;

            boolean passed = result.isPassed();
            executionMetrics.recordVerification(passed);

            if (!passed) {
                log.warn("[Harness] 输出验证未通过: status={}, reason={}",
                        result.getStatus(), result.getReason());
                return false;
            }

            return true;
        } catch (Exception e) {
            log.warn("[Harness] afterThink 降级: {}", e.getMessage());
            executionMetrics.recordVerification(true);
            return true;
        }
    }

    // === Execute 阶段钩子 ===

    /** execute() 之前：L2 工具校验 + L6 Guardrails */
    public boolean beforeExecute(List<AssistantMessage.ToolCall> toolCalls,
                                  List<ToolCallback> availableTools) {
        log.debug("[Harness] beforeExecute - validating {} tool calls", toolCalls.size());
        try {
            // L2: 工具调用验证
            ToolCallValidator.ValidationResult validationResult = toolCallValidator.validate(toolCalls, availableTools);
            if (!validationResult.isAllowed()) {
                log.warn("[Harness] 工具调用验证被拒绝: {}", validationResult.reason());
                return false;
            }
            if (validationResult.status() == ToolCallValidator.Status.WARN) {
                log.warn("[Harness] 工具调用验证警告: {}", validationResult.reason());
            }

            // L6: Guardrails 安全护栏
            if (config.isEnableGuardrails()) {
                for (AssistantMessage.ToolCall toolCall : toolCalls) {
                    Guardrails.ValidationResult guardrailResult =
                            guardrails.validateToolCall(toolCall.name(), toolCall.arguments());
                    if (!guardrailResult.isAllowed()) {
                        log.warn("[Harness] 安全护栏拒绝工具调用: tool={}, reason={}",
                                toolCall.name(), guardrailResult.getReason());
                        return false;
                    }
                    if (guardrailResult.getStatus() == Guardrails.ValidationResult.Status.WARN) {
                        log.warn("[Harness] 安全护栏警告: tool={}, reason={}",
                                toolCall.name(), guardrailResult.getReason());
                    }
                }
            }

            return true;
        } catch (Exception e) {
            log.warn("[Harness] beforeExecute 降级: {}", e.getMessage());
            return true;
        }
    }

    /** execute() 之后：L2 结果提炼 + L3 追踪 + L5 指标 */
    public void afterExecute(ToolResponseMessage toolResponseMessage) {
        log.debug("[Harness] afterExecute - processing tool response");
        try {
            // L2: 工具结果提炼（仅记录日志，不修改消息）
            Map<String, String> summarized = toolResultSummarizer.summarizeToolResponse(toolResponseMessage);
            for (Map.Entry<String, String> entry : summarized.entrySet()) {
                log.debug("[Harness] 工具结果摘要: tool={}, length={}",
                        entry.getKey(), entry.getValue() != null ? entry.getValue().length() : 0);
            }

            // L3: 更新 ExecutionTracker（记录工具调用成功）
            if (toolResponseMessage.getResponses() != null) {
                for (ToolResponseMessage.ToolResponse resp : toolResponseMessage.getResponses()) {
                    executionTracker.recordToolCall(resp.name(), true, 0);
                    executionMetrics.recordToolCall(true);
                }
            }
        } catch (Exception e) {
            log.warn("[Harness] afterExecute 降级: {}", e.getMessage());
        }
    }

    // === Step 级钩子 ===

    /** 每步完成后：L3 进度更新 + L4 状态更新 + L5 指标采集 */
    public void onStepComplete(int stepNumber, String agentState) {
        log.debug("[Harness] Step {} completed, state: {}", stepNumber, agentState);
        try {
            // L3: 更新执行追踪
            executionTracker.startStep(stepNumber);
            executionTracker.endStep(stepNumber, agentState, "completed", true);

            // L4: 更新结构化状态
            stateManager.recordStep(stepNumber, agentState, "completed", 0, true);

            // L5: 记录迭代指标
            executionMetrics.recordIteration(stepNumber);
        } catch (Exception e) {
            log.warn("[Harness] onStepComplete 降级: {}", e.getMessage());
        }
    }

    // === 错误处理钩子 ===

    /** 错误发生时：L6 错误恢复 */
    public boolean onError(Exception exception, String phase) {
        log.error("[Harness] Error in phase {}: {}", phase, exception.getMessage());
        try {
            ErrorRecoveryHandler.RecoveryResult recoveryResult;

            if ("EXECUTE".equalsIgnoreCase(phase)) {
                // 工具执行错误
                recoveryResult = errorRecoveryHandler.handleToolExecutionError(
                        exception, "unknown", null);
            } else if ("THINK".equalsIgnoreCase(phase)) {
                // LLM 调用错误
                recoveryResult = errorRecoveryHandler.handleLLMError(exception);
            } else {
                // 其他阶段错误，按 LLM 错误处理
                recoveryResult = errorRecoveryHandler.handleLLMError(exception);
            }

            log.info("[Harness] 错误恢复结果: {}", recoveryResult);

            // 使用 RetryPolicy 判断是否应重试
            if (recoveryResult.isRecovered()
                    && recoveryResult.getAction() == ErrorRecoveryHandler.RecoveryAction.RETRY) {
                boolean shouldRetry = retryPolicy.shouldRetry(exception, 1);
                if (shouldRetry) {
                    executionMetrics.recordRetry();
                    return true;
                }
            }

            return recoveryResult.isRecovered();
        } catch (Exception e) {
            log.warn("[Harness] onError 降级: {}", e.getMessage());
            return false;
        }
    }

    // === L3 规划钩子 ===

    /** 判断是否需要进行任务规划 */
    public boolean shouldPlan(String userMessage) {
        if (!config.isEnableTaskPlanning()) return false;
        try {
            log.debug("[Harness] Evaluating if planning is needed for: {}",
                      userMessage.substring(0, Math.min(50, userMessage.length())));
            return taskPlanner.shouldPlan(userMessage);
        } catch (Exception e) {
            log.warn("[Harness] shouldPlan 降级: {}", e.getMessage());
            return false;
        }
    }

    /** 执行任务规划，返回计划摘要文本 */
    public String plan(String userMessage, List<String> availableToolNames) {
        try {
            TaskPlanner.ExecutionPlan executionPlan = taskPlanner.plan(userMessage, availableToolNames);
            String summary = executionPlan.getSummary();
            log.info("[Harness] 执行计划: {}", summary);
            return summary;
        } catch (Exception e) {
            log.warn("[Harness] plan 降级: {}", e.getMessage());
            return "规划失败，使用直接处理模式";
        }
    }

    /** 获取执行指标报告 */
    public String getMetricsReport() {
        try {
            return executionMetrics.toReport();
        } catch (Exception e) {
            log.warn("[Harness] getMetricsReport 降级: {}", e.getMessage());
            return "{}";
        }
    }

    /** 当 afterThink 返回 false 时，生成修正提示词 */
    public String generateCorrectionHint() {
        try {
            if (lastVerificationResult == null) {
                return null;
            }
            return outputVerifier.generateCorrectionHint(lastVerificationResult);
        } catch (Exception e) {
            log.warn("[Harness] generateCorrectionHint 降级: {}", e.getMessage());
            return null;
        }
    }

    // === 辅助方法 ===

    /** 从对话上下文中提取最后一条用户消息文本 */
    private String extractLastUserMessage(List<Message> messages) {
        if (messages == null || messages.isEmpty()) return "";
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg instanceof org.springframework.ai.chat.messages.UserMessage userMsg) {
                return userMsg.getText();
            }
        }
        return "";
    }
}
