package com.kama.jchatmind.agent.harness.orchestration;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 执行追踪器
 * L3 执行编排层组件，追踪 Agent 执行过程中的每个步骤和工具调用情况
 */
@Slf4j
public class ExecutionTracker {

    /** 步骤记录列表 */
    private final List<StepRecord> stepHistory = new ArrayList<>();

    /** 运行开始时间 */
    private long runStartTimeMs;

    /** 总工具调用次数 */
    private int totalToolCalls;

    /** 成功工具调用次数 */
    private int successfulToolCalls;

    /** 失败工具调用次数 */
    private int failedToolCalls;

    /** 各步骤的开始时间缓存 */
    private final java.util.Map<Integer, Long> stepStartTimes = new java.util.HashMap<>();

    /**
     * 记录运行开始时间
     */
    public void startRun() {
        this.runStartTimeMs = System.currentTimeMillis();
        log.info("[ExecutionTracker] 运行开始");
    }

    /**
     * 记录步骤开始
     *
     * @param stepNumber 步骤编号
     */
    public void startStep(int stepNumber) {
        stepStartTimes.put(stepNumber, System.currentTimeMillis());
        log.debug("[ExecutionTracker] 步骤 {} 开始", stepNumber);
    }

    /**
     * 记录步骤结束
     *
     * @param stepNumber 步骤编号
     * @param action     执行的动作描述
     * @param result     执行结果
     * @param success    是否成功
     */
    public void endStep(int stepNumber, String action, String result, boolean success) {
        long startTime = stepStartTimes.getOrDefault(stepNumber, System.currentTimeMillis());
        long endTime = System.currentTimeMillis();
        StepRecord record = new StepRecord(stepNumber, action, result, startTime, endTime, success);
        stepHistory.add(record);
        log.debug("[ExecutionTracker] 步骤 {} 结束，耗时 {}ms，成功: {}", stepNumber, endTime - startTime, success);
    }

    /**
     * 记录工具调用
     *
     * @param toolName   工具名称
     * @param success    是否成功
     * @param durationMs 耗时（毫秒）
     */
    public void recordToolCall(String toolName, boolean success, long durationMs) {
        totalToolCalls++;
        if (success) {
            successfulToolCalls++;
        } else {
            failedToolCalls++;
        }
        log.debug("[ExecutionTracker] 工具调用: {} | 成功: {} | 耗时: {}ms", toolName, success, durationMs);
    }

    /**
     * 返回进度字符串
     *
     * @return 进度描述
     */
    public String getProgress() {
        long elapsed = getTotalDurationMs();
        double elapsedSec = elapsed / 1000.0;
        int maxSteps = 20; // 对应 JChatMind.MAX_STEPS
        return String.format("步骤 %d/%d, 已用时 %.1fs, 工具调用 %d 次",
                stepHistory.size(), maxSteps, elapsedSec, totalToolCalls);
    }

    /**
     * 返回步骤历史列表
     *
     * @return 步骤记录列表
     */
    public List<StepRecord> getStepHistory() {
        return List.copyOf(stepHistory);
    }

    /**
     * 返回总运行时长（毫秒）
     *
     * @return 总运行时长
     */
    public long getTotalDurationMs() {
        if (runStartTimeMs == 0) {
            return 0;
        }
        return System.currentTimeMillis() - runStartTimeMs;
    }

    /**
     * 返回工具调用成功率
     *
     * @return 成功率（0.0 ~ 1.0），无工具调用时返回 1.0
     */
    public double getToolCallSuccessRate() {
        if (totalToolCalls == 0) {
            return 1.0;
        }
        return (double) successfulToolCalls / totalToolCalls;
    }

    /**
     * 返回完整的执行报告
     *
     * @return 格式化的报告字符串
     */
    public String toReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("========== 执行报告 ==========\n");
        sb.append(String.format("总运行时长: %.2fs\n", getTotalDurationMs() / 1000.0));
        sb.append(String.format("执行步骤数: %d\n", stepHistory.size()));
        sb.append(String.format("工具调用总数: %d（成功: %d, 失败: %d）\n",
                totalToolCalls, successfulToolCalls, failedToolCalls));
        sb.append(String.format("工具调用成功率: %.1f%%\n", getToolCallSuccessRate() * 100));

        if (!stepHistory.isEmpty()) {
            sb.append("\n--- 步骤详情 ---\n");
            for (StepRecord record : stepHistory) {
                sb.append(String.format("  步骤 %d: %s | 耗时: %dms | %s\n",
                        record.stepNumber(),
                        record.action(),
                        record.endTimeMs() - record.startTimeMs(),
                        record.success() ? "成功" : "失败"));
                if (record.result() != null && !record.result().isBlank()) {
                    String truncatedResult = record.result().length() > 100
                            ? record.result().substring(0, 100) + "..."
                            : record.result();
                    sb.append(String.format("         结果: %s\n", truncatedResult));
                }
            }
        }

        sb.append("==============================");
        return sb.toString();
    }

    /**
     * 重置所有状态
     */
    public void reset() {
        stepHistory.clear();
        stepStartTimes.clear();
        runStartTimeMs = 0;
        totalToolCalls = 0;
        successfulToolCalls = 0;
        failedToolCalls = 0;
        log.debug("[ExecutionTracker] 状态已重置");
    }

    /**
     * 步骤记录
     */
    public record StepRecord(
            int stepNumber,
            String action,
            String result,
            long startTimeMs,
            long endTimeMs,
            boolean success
    ) {
    }
}
