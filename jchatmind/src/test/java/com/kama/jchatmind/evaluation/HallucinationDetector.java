package com.kama.jchatmind.evaluation;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 幻觉检测器
 */
public class HallucinationDetector {

    /**
     * 检测回答中是否包含不应出现的内容
     * @param response 实际回复
     * @param shouldNotContain 不应包含的内容列表
     * @return 是否检测到幻觉（包含了不应出现的内容）
     */
    public boolean detectByForbiddenContent(String response, List<String> shouldNotContain) {
        if (response == null || response.isEmpty()) {
            return false;
        }
        if (shouldNotContain == null || shouldNotContain.isEmpty()) {
            return false;
        }
        return shouldNotContain.stream().anyMatch(response::contains);
    }

    /**
     * 检测回答是否超出知识库范围
     * @param response 实际回复
     * @param knowledgeBaseContent 知识库内容
     * @return 是否检测到超出范围的幻觉
     */
    public boolean detectByKnowledgeScope(String response, String knowledgeBaseContent) {
        if (response == null || response.isEmpty()) {
            return false;
        }
        if (knowledgeBaseContent == null || knowledgeBaseContent.isEmpty()) {
            return false;
        }

        // 简单策略：将回复分成句子，检查每个句子的关键词是否在知识库中有对应
        String[] sentences = response.split("[。！？；\\n]");
        int totalSentences = 0;
        int unsupportedSentences = 0;

        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.length() < 5) {
                continue; // 跳过太短的片段
            }
            totalSentences++;

            // 提取句子中的关键词（长度>=2的词组）
            String[] words = extractKeyPhrases(sentence);
            boolean hasSupport = false;
            for (String word : words) {
                if (knowledgeBaseContent.contains(word)) {
                    hasSupport = true;
                    break;
                }
            }
            if (!hasSupport) {
                unsupportedSentences++;
            }
        }

        if (totalSentences == 0) {
            return false;
        }
        // 如果超过50%的句子没有知识库支持，则判定为幻觉
        return (double) unsupportedSentences / totalSentences > 0.5;
    }

    /**
     * 检测事实性错误
     * @param response 实际回复
     * @param factMap 已知事实集 (键为事实描述，值为正确答案)
     * @return 是否检测到事实性错误
     */
    public boolean detectFactualError(String response, Map<String, String> factMap) {
        if (response == null || response.isEmpty() || factMap == null || factMap.isEmpty()) {
            return false;
        }

        for (Map.Entry<String, String> entry : factMap.entrySet()) {
            String factKey = entry.getKey();
            String correctValue = entry.getValue();

            // 如果回复中提到了这个事实的主题但答案不正确
            if (response.contains(factKey) && !response.contains(correctValue)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 综合幻觉率计算
     * @param results 测试结果列表
     * @return 幻觉率 (0.0 ~ 1.0)
     */
    public double calculateHallucinationRate(List<TestResult> results) {
        if (results == null || results.isEmpty()) {
            return 0.0;
        }
        long hallucinationCount = results.stream()
                .filter(TestResult::isHallucinationDetected)
                .count();
        return (double) hallucinationCount / results.size();
    }

    /**
     * 从句子中提取关键短语
     */
    private String[] extractKeyPhrases(String sentence) {
        // 简单实现：按标点和常见停用词分割，取长度>=2的片段
        return Arrays.stream(sentence.split("[，,、 的了是在有和与对于]"))
                .map(String::trim)
                .filter(s -> s.length() >= 2)
                .toArray(String[]::new);
    }
}
