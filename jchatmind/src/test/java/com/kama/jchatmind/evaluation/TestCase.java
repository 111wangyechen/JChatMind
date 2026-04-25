package com.kama.jchatmind.evaluation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 测试用例数据模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TestCase {
    private String id;
    private String name;
    private String description;
    private String category;
    private TestInput input;
    private TestExpected expected;
    private EvaluationCriteria evaluationCriteria;

    /**
     * 测试输入
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TestInput {
        private String userMessage;
        private List<ConversationMessage> conversationHistory;
        private List<String> availableTools;
        private Object knowledgeBase;
    }

    /**
     * 对话消息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConversationMessage {
        private String role;
        private String content;
    }

    /**
     * 预期结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TestExpected {
        private boolean shouldCallTool;
        private String expectedToolName;
        private List<String> expectedKeywords;
        private List<String> shouldContain;
        private List<String> shouldNotContain;
        private String expectedBehavior;
    }

    /**
     * 评估标准
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EvaluationCriteria {
        private double accuracyWeight;
        private boolean hallucinationCheck;
        private boolean relevanceCheck;
    }
}
