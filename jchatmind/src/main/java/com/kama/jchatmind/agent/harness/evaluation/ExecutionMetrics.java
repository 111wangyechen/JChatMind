package com.kama.jchatmind.agent.harness.evaluation;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 执行指标采集器
 * 采集 Agent 执行全过程的指标，用于观测和评估
 */
@Data
@Slf4j
public class ExecutionMetrics {

    /** 运行开始时间（毫秒） */
    private long runStartTimeMs;

    /** 运行结束时间（毫秒） */
    private long runEndTimeMs;

    /** 总迭代次数 */
    private int totalIterations;

    /** 总工具调用次数 */
    private int totalToolCalls;

    /** 成功工具调用次数 */
    private int successfulToolCalls;

    /** 失败工具调用次数 */
    private int failedToolCalls;

    /** 上下文压缩次数 */
    private int contextCompressions;

    /** 自验证通过次数 */
    private int verificationPasses;

    /** 自验证重试次数 */
    private int verificationRetries;

    /** 估算输入 Token 数 */
    private int estimatedInputTokens;

    /** 估算输出 Token 数 */
    private int estimatedOutputTokens;

    /** 重试次数 */
    private int retryAttempts;

    /** 每步指标列表 */
    private List<StepMetric> stepMetrics = new ArrayList<>();

    /**
     * 每步指标记录
     */
    public record StepMetric(
            int step,
            long durationMs,
            String action,
            int toolCalls,
            boolean verificationPassed
    ) {}

    /** 记录运行开始 */
    public void startRun() {
        this.runStartTimeMs = System.currentTimeMillis();
        log.debug("[ExecutionMetrics] 运行开始，时间戳: {}", runStartTimeMs);
    }

    /** 记录运行结束 */
    public void endRun() {
        this.runEndTimeMs = System.currentTimeMillis();
        log.debug("[ExecutionMetrics] 运行结束，总耗时: {}ms", getTotalDurationMs());
    }

    /** 记录一次迭代 */
    public void recordIteration(int step) {
        this.totalIterations = step;
        log.debug("[ExecutionMetrics] 记录迭代，当前步数: {}", step);
    }

    /** 记录一次工具调用 */
    public void recordToolCall(boolean success) {
        this.totalToolCalls++;
        if (success) {
            this.successfulToolCalls++;
        } else {
            this.failedToolCalls++;
        }
        log.debug("[ExecutionMetrics] 记录工具调用，success={}, total={}", success, totalToolCalls);
    }

    /** 记录一次上下文压缩 */
    public void recordContextCompression() {
        this.contextCompressions++;
        log.debug("[ExecutionMetrics] 记录上下文压缩，总次数: {}", contextCompressions);
    }

    /** 记录一次验证结果 */
    public void recordVerification(boolean passed) {
        if (passed) {
            this.verificationPasses++;
        } else {
            this.verificationRetries++;
        }
        log.debug("[ExecutionMetrics] 记录验证，passed={}, passes={}, retries={}",
                passed, verificationPasses, verificationRetries);
    }

    /** 记录 Token 用量 */
    public void recordTokenUsage(int inputTokens, int outputTokens) {
        this.estimatedInputTokens += inputTokens;
        this.estimatedOutputTokens += outputTokens;
        log.debug("[ExecutionMetrics] 记录 Token 用量，input={}, output={}", inputTokens, outputTokens);
    }

    /** 记录一次重试 */
    public void recordRetry() {
        this.retryAttempts++;
        log.debug("[ExecutionMetrics] 记录重试，总次数: {}", retryAttempts);
    }

    /** 添加步骤指标 */
    public void addStepMetric(StepMetric metric) {
        this.stepMetrics.add(metric);
        log.debug("[ExecutionMetrics] 添加步骤指标: step={}, action={}, duration={}ms",
                metric.step(), metric.action(), metric.durationMs());
    }

    /** 获取总耗时（毫秒） */
    public long getTotalDurationMs() {
        if (runEndTimeMs <= 0 || runStartTimeMs <= 0) {
            return 0;
        }
        return runEndTimeMs - runStartTimeMs;
    }

    /** 获取工具调用成功率（百分比） */
    public double getToolCallSuccessRate() {
        if (totalToolCalls == 0) {
            return 100.0;
        }
        return (double) successfulToolCalls / totalToolCalls * 100.0;
    }

    /** 获取验证通过率（百分比） */
    public double getVerificationPassRate() {
        int total = verificationPasses + verificationRetries;
        if (total == 0) {
            return 100.0;
        }
        return (double) verificationPasses / total * 100.0;
    }

    /** 获取总 Token 数 */
    public int getTotalTokens() {
        return estimatedInputTokens + estimatedOutputTokens;
    }

    /**
     * 生成 JSON 格式报告字符串（手动拼接）
     */
    public String toReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"duration_ms\": ").append(getTotalDurationMs()).append(",\n");
        sb.append("  \"iterations\": ").append(totalIterations).append(",\n");

        // 工具调用
        sb.append("  \"tool_calls\": {");
        sb.append("\"total\": ").append(totalToolCalls).append(", ");
        sb.append("\"success\": ").append(successfulToolCalls).append(", ");
        sb.append("\"failed\": ").append(failedToolCalls).append(", ");
        sb.append("\"success_rate\": \"").append(String.format("%.1f%%", getToolCallSuccessRate())).append("\"");
        sb.append("},\n");

        // Token
        sb.append("  \"tokens\": {");
        sb.append("\"input\": ").append(estimatedInputTokens).append(", ");
        sb.append("\"output\": ").append(estimatedOutputTokens).append(", ");
        sb.append("\"total\": ").append(getTotalTokens());
        sb.append("},\n");

        // 验证
        sb.append("  \"verification\": {");
        sb.append("\"passes\": ").append(verificationPasses).append(", ");
        sb.append("\"retries\": ").append(verificationRetries).append(", ");
        sb.append("\"pass_rate\": \"").append(String.format("%.1f%%", getVerificationPassRate())).append("\"");
        sb.append("},\n");

        sb.append("  \"context_compressions\": ").append(contextCompressions).append(",\n");
        sb.append("  \"retry_attempts\": ").append(retryAttempts).append("\n");
        sb.append("}");

        return sb.toString();
    }

    /**
     * 生成简洁摘要字符串（一行），用于日志输出
     */
    public String toSummary() {
        return String.format(
                "[Metrics] duration=%dms, iterations=%d, toolCalls=%d(success=%d/failed=%d), " +
                        "tokens=%d(in=%d/out=%d), verification(pass=%d/retry=%d), " +
                        "compressions=%d, retries=%d",
                getTotalDurationMs(), totalIterations,
                totalToolCalls, successfulToolCalls, failedToolCalls,
                getTotalTokens(), estimatedInputTokens, estimatedOutputTokens,
                verificationPasses, verificationRetries,
                contextCompressions, retryAttempts
        );
    }

    /**
     * 重置所有指标
     */
    public void reset() {
        this.runStartTimeMs = 0;
        this.runEndTimeMs = 0;
        this.totalIterations = 0;
        this.totalToolCalls = 0;
        this.successfulToolCalls = 0;
        this.failedToolCalls = 0;
        this.contextCompressions = 0;
        this.verificationPasses = 0;
        this.verificationRetries = 0;
        this.estimatedInputTokens = 0;
        this.estimatedOutputTokens = 0;
        this.retryAttempts = 0;
        this.stepMetrics = new ArrayList<>();
        log.debug("[ExecutionMetrics] 指标已重置");
    }
}
