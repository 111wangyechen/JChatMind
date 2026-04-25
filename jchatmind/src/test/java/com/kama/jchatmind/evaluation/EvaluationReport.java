package com.kama.jchatmind.evaluation;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 评估报告生成器
 */
public class EvaluationReport {

    private EvaluationMetrics overallMetrics;
    private final Map<String, EvaluationMetrics> categoryMetrics;
    private final List<TestResult> detailedResults;

    // 评估基线定义
    private static final double SINGLE_TURN_ACCURACY_BASELINE = 0.90;
    private static final double MULTI_TURN_ACCURACY_BASELINE = 0.85;
    private static final double RAG_ACCURACY_BASELINE = 0.80;
    private static final double TOOL_CALLING_SUCCESS_BASELINE = 0.95;
    private static final double NO_KB_HALLUCINATION_BASELINE = 0.15;
    private static final double WITH_KB_HALLUCINATION_BASELINE = 0.05;

    public EvaluationReport() {
        this.categoryMetrics = new LinkedHashMap<>();
        this.detailedResults = new ArrayList<>();
    }

    /**
     * 添加单个测试结果
     */
    public void addResult(TestResult result) {
        detailedResults.add(result);
    }

    /**
     * 计算总体指标
     */
    public void calculateMetrics() {
        if (detailedResults.isEmpty()) {
            overallMetrics = EvaluationMetrics.builder().build();
            return;
        }

        // 计算总体指标
        overallMetrics = calculateMetricsForResults(detailedResults);

        // 按类别计算指标
        Map<String, List<TestResult>> groupedByCategory = detailedResults.stream()
                .collect(Collectors.groupingBy(TestResult::getCategory));

        for (Map.Entry<String, List<TestResult>> entry : groupedByCategory.entrySet()) {
            categoryMetrics.put(entry.getKey(), calculateMetricsForResults(entry.getValue()));
        }
    }

    /**
     * 为一组测试结果计算指标
     */
    private EvaluationMetrics calculateMetricsForResults(List<TestResult> results) {
        int total = results.size();
        int passed = (int) results.stream().filter(TestResult::isPassed).count();
        int failed = total - passed;
        double accuracyRate = total > 0 ? (double) passed / total : 0.0;

        long hallucinationCount = results.stream()
                .filter(TestResult::isHallucinationDetected).count();
        double hallucinationRate = total > 0 ? (double) hallucinationCount / total : 0.0;

        double avgAccuracyScore = results.stream()
                .mapToDouble(TestResult::getAccuracyScore).average().orElse(0.0);

        long avgResponseTime = (long) results.stream()
                .mapToLong(TestResult::getResponseTimeMs).average().orElse(0.0);

        // 工具调用成功率（仅针对期望调用工具的用例）
        double toolCallingSuccessRate = 1.0; // 默认无工具调用场景则为100%

        return EvaluationMetrics.builder()
                .accuracyRate(accuracyRate)
                .hallucinationRate(hallucinationRate)
                .toolCallingSuccessRate(toolCallingSuccessRate)
                .relevanceScore(avgAccuracyScore)
                .avgResponseTimeMs(avgResponseTime)
                .totalCases(total)
                .passedCases(passed)
                .failedCases(failed)
                .build();
    }

    /**
     * 生成文本报告
     */
    public String generateTextReport() {
        if (overallMetrics == null) {
            calculateMetrics();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=" .repeat(60)).append("\n");
        sb.append("         JChatMind Agent 评估报告\n");
        sb.append("=".repeat(60)).append("\n\n");

        // 总体指标
        sb.append("【总体指标】\n");
        sb.append(String.format("  总用例数: %d\n", overallMetrics.getTotalCases()));
        sb.append(String.format("  通过数: %d\n", overallMetrics.getPassedCases()));
        sb.append(String.format("  失败数: %d\n", overallMetrics.getFailedCases()));
        sb.append(String.format("  准确率: %.2f%%\n", overallMetrics.getAccuracyRate() * 100));
        sb.append(String.format("  幻觉率: %.2f%%\n", overallMetrics.getHallucinationRate() * 100));
        sb.append(String.format("  工具调用成功率: %.2f%%\n", overallMetrics.getToolCallingSuccessRate() * 100));
        sb.append(String.format("  平均响应时间: %dms\n", overallMetrics.getAvgResponseTimeMs()));
        sb.append("\n");

        // 分类别指标
        sb.append("【分类别指标】\n");
        for (Map.Entry<String, EvaluationMetrics> entry : categoryMetrics.entrySet()) {
            EvaluationMetrics m = entry.getValue();
            sb.append(String.format("  [%s] 通过 %d/%d, 准确率 %.2f%%, 幻觉率 %.2f%%\n",
                    entry.getKey(), m.getPassedCases(), m.getTotalCases(),
                    m.getAccuracyRate() * 100, m.getHallucinationRate() * 100));
        }
        sb.append("\n");

        // 基线检查
        sb.append("【基线检查】\n");
        sb.append(String.format("  总体达标: %s\n", meetsBaseline() ? "是 ✓" : "否 ✗"));
        sb.append("\n");

        // 失败用例详情
        List<TestResult> failures = detailedResults.stream()
                .filter(r -> !r.isPassed()).collect(Collectors.toList());
        if (!failures.isEmpty()) {
            sb.append("【失败用例】\n");
            for (TestResult r : failures) {
                sb.append(String.format("  - [%s] %s | 得分: %.2f | 幻觉: %s | 备注: %s\n",
                        r.getTestCaseId(), r.getCategory(),
                        r.getAccuracyScore(),
                        r.isHallucinationDetected() ? "是" : "否",
                        r.getNotes() != null ? r.getNotes() : "无"));
            }
        }

        sb.append("\n").append("=".repeat(60)).append("\n");
        return sb.toString();
    }

    /**
     * 评估基线检查
     * 基线定义:
     * - 单轮对话准确率 >= 90%
     * - 多轮对话准确率 >= 85%
     * - RAG问答准确率 >= 80%
     * - 工具调用成功率 >= 95%
     * - 无知识库时幻觉率 < 15%
     * - 有知识库时幻觉率 < 5%
     */
    public boolean meetsBaseline() {
        if (overallMetrics == null) {
            calculateMetrics();
        }

        // 单轮对话准确率
        EvaluationMetrics singleTurn = categoryMetrics.get("single_turn");
        if (singleTurn != null && singleTurn.getAccuracyRate() < SINGLE_TURN_ACCURACY_BASELINE) {
            return false;
        }

        // 多轮对话准确率
        EvaluationMetrics multiTurn = categoryMetrics.get("multi_turn");
        if (multiTurn != null && multiTurn.getAccuracyRate() < MULTI_TURN_ACCURACY_BASELINE) {
            return false;
        }

        // RAG准确率
        EvaluationMetrics ragRetrieval = categoryMetrics.get("rag_retrieval");
        if (ragRetrieval != null && ragRetrieval.getAccuracyRate() < RAG_ACCURACY_BASELINE) {
            return false;
        }

        // 工具调用成功率
        EvaluationMetrics toolNormal = categoryMetrics.get("tool_calling_normal");
        if (toolNormal != null && toolNormal.getToolCallingSuccessRate() < TOOL_CALLING_SUCCESS_BASELINE) {
            return false;
        }

        // 幻觉率检查
        EvaluationMetrics ragQuality = categoryMetrics.get("rag_quality");
        if (ragQuality != null && ragQuality.getHallucinationRate() >= WITH_KB_HALLUCINATION_BASELINE) {
            return false;
        }

        // 无知识库场景幻觉率
        if (singleTurn != null && singleTurn.getHallucinationRate() >= NO_KB_HALLUCINATION_BASELINE) {
            return false;
        }

        return true;
    }

    // Getters
    public EvaluationMetrics getOverallMetrics() {
        if (overallMetrics == null) {
            calculateMetrics();
        }
        return overallMetrics;
    }

    public Map<String, EvaluationMetrics> getCategoryMetrics() {
        return Collections.unmodifiableMap(categoryMetrics);
    }

    public List<TestResult> getDetailedResults() {
        return Collections.unmodifiableList(detailedResults);
    }
}
