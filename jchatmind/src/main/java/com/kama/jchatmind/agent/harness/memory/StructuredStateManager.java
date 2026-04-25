package com.kama.jchatmind.agent.harness.memory;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 结构化状态管理器
 * 管理 Agent 执行过程中的结构化状态，包括任务进度、步骤历史和中间结果
 */
@Slf4j
public class StructuredStateManager {

    private TaskState state;

    /**
     * 步骤执行结果
     */
    public record StepResult(
            int stepNumber,
            String action,
            String result,
            long durationMs,
            boolean success
    ) {
    }

    /**
     * 任务状态
     */
    @Data
    @Builder
    public static class TaskState {
        /** 当前任务描述 */
        private String currentTask;
        /** 计划总步骤数 */
        private int totalSteps;
        /** 已完成步骤数 */
        private int completedSteps;
        /** 步骤执行历史 */
        @Builder.Default
        private List<StepResult> stepHistory = new ArrayList<>();
        /** 中间结果键值对 */
        @Builder.Default
        private Map<String, String> intermediateResults = new LinkedHashMap<>();
        /** 开始时间 */
        private long startTimeMs;
    }

    /**
     * 初始化任务状态
     *
     * @param task 任务描述
     */
    public void initState(String task) {
        this.state = TaskState.builder()
                .currentTask(task)
                .totalSteps(0)
                .completedSteps(0)
                .stepHistory(new ArrayList<>())
                .intermediateResults(new LinkedHashMap<>())
                .startTimeMs(System.currentTimeMillis())
                .build();
        log.info("[状态管理] 初始化任务状态: {}", task);
    }

    /**
     * 记录步骤执行结果
     *
     * @param stepNumber 步骤编号
     * @param action     执行的动作
     * @param result     执行结果
     * @param durationMs 执行耗时（毫秒）
     * @param success    是否成功
     */
    public void recordStep(int stepNumber, String action, String result, long durationMs, boolean success) {
        if (state == null) {
            log.warn("[状态管理] 尚未初始化状态，无法记录步骤");
            return;
        }

        StepResult stepResult = new StepResult(stepNumber, action, result, durationMs, success);
        state.getStepHistory().add(stepResult);
        if (success) {
            state.setCompletedSteps(state.getCompletedSteps() + 1);
        }
        // 更新总步骤数（取最大值）
        if (stepNumber > state.getTotalSteps()) {
            state.setTotalSteps(stepNumber);
        }

        log.debug("[状态管理] 记录步骤 #{}: action={}, success={}, duration={}ms",
                stepNumber, action, success, durationMs);
    }

    /**
     * 更新中间结果
     *
     * @param key   结果键
     * @param value 结果值
     */
    public void updateIntermediateResult(String key, String value) {
        if (state == null) {
            log.warn("[状态管理] 尚未初始化状态，无法更新中间结果");
            return;
        }
        state.getIntermediateResults().put(key, value);
        log.debug("[状态管理] 更新中间结果: {} = {}", key,
                value.length() > 50 ? value.substring(0, 50) + "..." : value);
    }

    /**
     * 获取当前状态的格式化字符串（可注入到 system prompt）
     *
     * @return 格式化的状态快照
     */
    public String getStateSnapshot() {
        if (state == null) {
            return "[状态] 尚未初始化";
        }

        long elapsedMs = System.currentTimeMillis() - state.getStartTimeMs();
        StringBuilder sb = new StringBuilder();
        sb.append("=== 当前任务状态 ===\n");
        sb.append("任务: ").append(state.getCurrentTask()).append("\n");
        sb.append("进度: ").append(state.getCompletedSteps()).append("/")
                .append(state.getTotalSteps()).append(" 步\n");
        sb.append("耗时: ").append(elapsedMs / 1000).append("s\n");

        // 最近 3 条步骤记录
        List<StepResult> history = state.getStepHistory();
        if (!history.isEmpty()) {
            sb.append("最近步骤:\n");
            int start = Math.max(0, history.size() - 3);
            for (int i = start; i < history.size(); i++) {
                StepResult step = history.get(i);
                sb.append(String.format("  #%d [%s] %s -> %s (%dms)\n",
                        step.stepNumber(),
                        step.success() ? "成功" : "失败",
                        step.action(),
                        step.result() != null && step.result().length() > 50
                                ? step.result().substring(0, 50) + "..." : step.result(),
                        step.durationMs()));
            }
        }

        // 中间结果
        if (!state.getIntermediateResults().isEmpty()) {
            sb.append("中间结果:\n");
            for (Map.Entry<String, String> entry : state.getIntermediateResults().entrySet()) {
                String val = entry.getValue();
                sb.append("  ").append(entry.getKey()).append(": ")
                        .append(val.length() > 80 ? val.substring(0, 80) + "..." : val)
                        .append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 回滚到指定步骤（移除该步骤之后的所有记录）
     *
     * @param stepNumber 要回滚到的步骤编号
     */
    public void rollbackToStep(int stepNumber) {
        if (state == null) {
            log.warn("[状态管理] 尚未初始化状态，无法回滚");
            return;
        }

        List<StepResult> history = state.getStepHistory();
        // 移除 stepNumber 之后的所有记录
        history.removeIf(step -> step.stepNumber() > stepNumber);

        // 重新计算已完成步骤数
        int completedCount = (int) history.stream().filter(StepResult::success).count();
        state.setCompletedSteps(completedCount);

        // 更新总步骤数
        state.setTotalSteps(history.isEmpty() ? 0 :
                history.stream().mapToInt(StepResult::stepNumber).max().orElse(0));

        log.info("[状态管理] 回滚到步骤 #{}，当前历史记录数: {}", stepNumber, history.size());
    }

    /**
     * 获取当前 TaskState 对象
     *
     * @return 当前任务状态，可能为 null
     */
    public TaskState getState() {
        return state;
    }

    /**
     * 重置状态
     */
    public void reset() {
        this.state = null;
        log.info("[状态管理] 状态已重置");
    }
}
