package com.kama.jchatmind.evaluation;

import java.util.List;

/**
 * 回答准确率评估器
 */
public class AccuracyEvaluator {

    /**
     * 基于关键词匹配评估准确率
     * @param response 实际回复
     * @param expectedKeywords 期望出现的关键词
     * @return 匹配率 (0.0 ~ 1.0)
     */
    public double evaluateByKeywords(String response, List<String> expectedKeywords) {
        if (expectedKeywords == null || expectedKeywords.isEmpty()) {
            return 1.0;
        }
        if (response == null || response.isEmpty()) {
            return 0.0;
        }

        long matchCount = expectedKeywords.stream()
                .filter(keyword -> response.contains(keyword))
                .count();

        return (double) matchCount / expectedKeywords.size();
    }

    /**
     * 基于必须包含/不应包含规则评估
     * @param response 实际回复
     * @param shouldContain 必须包含的内容
     * @param shouldNotContain 不应包含的内容
     * @return 得分 (0.0 ~ 1.0)
     */
    public double evaluateByContainRules(String response, List<String> shouldContain, List<String> shouldNotContain) {
        if (response == null) {
            response = "";
        }

        int totalRules = 0;
        int passedRules = 0;

        // 检查 shouldContain
        if (shouldContain != null && !shouldContain.isEmpty()) {
            for (String item : shouldContain) {
                totalRules++;
                if (response.contains(item)) {
                    passedRules++;
                }
            }
        }

        // 检查 shouldNotContain
        if (shouldNotContain != null && !shouldNotContain.isEmpty()) {
            for (String item : shouldNotContain) {
                totalRules++;
                if (!response.contains(item)) {
                    passedRules++;
                }
            }
        }

        if (totalRules == 0) {
            return 1.0;
        }
        return (double) passedRules / totalRules;
    }

    /**
     * 基于工具调用正确性评估
     * @param actualToolCalled 实际是否调用了工具
     * @param expectedToolCall 期望是否调用工具
     * @param actualToolName 实际调用的工具名
     * @param expectedToolName 期望的工具名
     * @return 工具调用是否正确
     */
    public boolean evaluateToolCalling(boolean actualToolCalled, boolean expectedToolCall,
                                       String actualToolName, String expectedToolName) {
        if (expectedToolCall != actualToolCalled) {
            return false;
        }
        if (expectedToolCall && expectedToolName != null) {
            return expectedToolName.equals(actualToolName);
        }
        return true;
    }

    /**
     * 综合评估
     * @param testCase 测试用例
     * @param actualResponse 实际回复
     * @param toolCalled 是否调用了工具
     * @param toolName 调用的工具名
     * @return 综合得分 (0.0 ~ 1.0)
     */
    public double evaluate(TestCase testCase, String actualResponse, boolean toolCalled, String toolName) {
        TestCase.TestExpected expected = testCase.getExpected();
        TestCase.EvaluationCriteria criteria = testCase.getEvaluationCriteria();

        double keywordScore = evaluateByKeywords(actualResponse, expected.getExpectedKeywords());
        double containScore = evaluateByContainRules(actualResponse,
                expected.getShouldContain(), expected.getShouldNotContain());

        boolean toolCorrect = evaluateToolCalling(toolCalled, expected.isShouldCallTool(),
                toolName, expected.getExpectedToolName());

        // 加权计算
        double weight = criteria.getAccuracyWeight();
        double score = (keywordScore * 0.4 + containScore * 0.4 + (toolCorrect ? 1.0 : 0.0) * 0.2) * weight;

        return Math.min(1.0, Math.max(0.0, score));
    }
}
