package com.kama.jchatmind.evaluation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评估指标汇总
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationMetrics {
    /** 准确率 */
    private double accuracyRate;
    /** 幻觉率 */
    private double hallucinationRate;
    /** 工具调用成功率 */
    private double toolCallingSuccessRate;
    /** 相关性得分 */
    private double relevanceScore;
    /** 平均响应时间（毫秒） */
    private long avgResponseTimeMs;
    /** 总用例数 */
    private int totalCases;
    /** 通过用例数 */
    private int passedCases;
    /** 失败用例数 */
    private int failedCases;

    @Override
    public String toString() {
        return String.format(
                "EvaluationMetrics {\n" +
                "  accuracyRate=%.2f%%,\n" +
                "  hallucinationRate=%.2f%%,\n" +
                "  toolCallingSuccessRate=%.2f%%,\n" +
                "  relevanceScore=%.2f,\n" +
                "  avgResponseTimeMs=%dms,\n" +
                "  totalCases=%d,\n" +
                "  passedCases=%d,\n" +
                "  failedCases=%d\n" +
                "}",
                accuracyRate * 100, hallucinationRate * 100,
                toolCallingSuccessRate * 100, relevanceScore,
                avgResponseTimeMs, totalCases, passedCases, failedCases
        );
    }
}
