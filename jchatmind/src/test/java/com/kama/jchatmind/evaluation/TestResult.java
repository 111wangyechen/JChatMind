package com.kama.jchatmind.evaluation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个测试用例的执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestResult {
    private String testCaseId;
    private String category;
    private boolean passed;
    private double accuracyScore;
    private boolean hallucinationDetected;
    private String actualResponse;
    private String notes;
    private long responseTimeMs;
}
