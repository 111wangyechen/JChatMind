package com.kama.jchatmind.agent.harness.evaluation;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 输出自验证器
 * 验证 Agent 的 LLM 输出质量，让 Agent 具备"自知之明"
 */
@Slf4j
public class OutputVerifier {

    /** 中文/英文常见停用词 */
    private static final Set<String> STOP_WORDS = Set.of(
            "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "一个",
            "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好",
            "自己", "这", "他", "她", "它", "们", "那", "些", "什么", "怎么", "如何", "可以",
            "吗", "吧", "呢", "啊", "哦", "嗯", "请", "帮", "帮我", "能", "能不能", "想",
            "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "shall", "should",
            "may", "might", "must", "can", "could", "i", "you", "he", "she", "it",
            "we", "they", "me", "him", "her", "us", "them", "my", "your", "his",
            "its", "our", "their", "this", "that", "these", "those", "what", "which",
            "who", "whom", "how", "where", "when", "why", "and", "or", "but", "if",
            "then", "so", "no", "not", "on", "in", "at", "to", "for", "of", "with",
            "by", "from", "as", "into", "about", "please", "help"
    );

    /**
     * 主验证方法：调用多个验证维度，汇总结果
     *
     * @param output              LLM 输出的 AssistantMessage
     * @param userMessage         用户原始消息
     * @param conversationContext 完整对话上下文
     * @return 验证结果
     */
    public VerificationResult verify(AssistantMessage output, String userMessage,
                                     List<Message> conversationContext) {
        String outputText = output.getText() != null ? output.getText() : "";
        List<AssistantMessage.ToolCall> toolCalls = output.getToolCalls() != null
                ? output.getToolCalls() : List.of();

        List<String> allIssues = new ArrayList<>();
        double totalScore = 0.0;
        int checks = 0;

        // 1. 相关性检查
        CheckResult relevance = checkRelevance(outputText, userMessage);
        if (!relevance.passed) {
            allIssues.add("相关性: " + relevance.detail);
        }
        totalScore += relevance.score;
        checks++;

        // 2. 完整性检查
        CheckResult completeness = checkCompleteness(outputText, toolCalls);
        if (!completeness.passed) {
            allIssues.add("完整性: " + completeness.detail);
        }
        totalScore += completeness.score;
        checks++;

        // 3. 工具调用合理性检查
        CheckResult toolReasonability = checkToolCallReasonability(toolCalls);
        if (!toolReasonability.passed) {
            allIssues.add("工具调用: " + toolReasonability.detail);
        }
        totalScore += toolReasonability.score;
        checks++;

        // 4. 一致性检查
        CheckResult consistency = checkConsistency(outputText, conversationContext);
        if (!consistency.passed) {
            allIssues.add("一致性: " + consistency.detail);
        }
        totalScore += consistency.score;
        checks++;

        // 汇总
        double avgScore = checks > 0 ? totalScore / checks : 1.0;

        if (allIssues.isEmpty()) {
            log.debug("[OutputVerifier] 验证通过，信心分数: {}", String.format("%.2f", avgScore));
            return VerificationResult.pass();
        }

        // 判断严重程度：有任一关键维度严重失败 → CRITICAL
        boolean hasCritical = !relevance.passed && !completeness.passed;
        if (hasCritical || avgScore < 0.3) {
            String reason = "多个验证维度严重不通过";
            log.warn("[OutputVerifier] 验证严重失败: {}, issues: {}", reason, allIssues);
            return VerificationResult.critical(reason);
        }

        String reason = String.format("发现 %d 个问题，信心分数: %.2f", allIssues.size(), avgScore);
        log.info("[OutputVerifier] 验证需重试: {}, issues: {}", reason, allIssues);
        return VerificationResult.needsRetry(reason, allIssues);
    }

    /**
     * 相关性检查：提取用户消息中的关键词（去除停用词后的实词），检查输出中是否包含至少一个关键词
     */
    private CheckResult checkRelevance(String outputText, String userMessage) {
        if (outputText.isBlank()) {
            return new CheckResult(false, 0.0, "输出为空文本，标记为不相关");
        }

        // 提取用户消息中的关键词（按空格和常见标点分词）
        String[] tokens = userMessage.split("[\\s,，。！？!?；;：:、\\-]+");
        List<String> keywords = Arrays.stream(tokens)
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .map(String::toLowerCase)
                .filter(t -> !STOP_WORDS.contains(t))
                .toList();

        if (keywords.isEmpty()) {
            // 无法提取关键词时默认通过
            return new CheckResult(true, 0.8, "无法提取关键词，默认通过");
        }

        String outputLower = outputText.toLowerCase();
        long matchCount = keywords.stream()
                .filter(outputLower::contains)
                .count();

        if (matchCount == 0) {
            return new CheckResult(false, 0.2, "输出未包含任何用户关键词: " + keywords);
        }

        double score = Math.min(1.0, (double) matchCount / keywords.size() + 0.3);
        return new CheckResult(true, score, "匹配了 " + matchCount + "/" + keywords.size() + " 个关键词");
    }

    /**
     * 完整性检查：
     * - 无工具调用时，输出文本不应为空
     * - 有工具调用时，应该有对应的工具名称
     * - 输出长度过短（< 5 字符）且无工具调用 → 不完整
     */
    private CheckResult checkCompleteness(String outputText, List<AssistantMessage.ToolCall> toolCalls) {
        boolean hasToolCalls = toolCalls != null && !toolCalls.isEmpty();

        if (!hasToolCalls) {
            if (outputText.isBlank()) {
                return new CheckResult(false, 0.0, "无工具调用且输出为空");
            }
            if (outputText.trim().length() < 5) {
                return new CheckResult(false, 0.2, "无工具调用且输出过短（< 5 字符）");
            }
        }

        if (hasToolCalls) {
            boolean allHaveName = toolCalls.stream()
                    .allMatch(tc -> tc.name() != null && !tc.name().isBlank());
            if (!allHaveName) {
                return new CheckResult(false, 0.3, "存在没有名称的工具调用");
            }
        }

        return new CheckResult(true, 1.0, "完整性检查通过");
    }

    /**
     * 工具调用合理性检查：
     * - 单次不应超过 5 个工具调用
     * - 不应同时调用 terminate 和其他工具
     */
    private CheckResult checkToolCallReasonability(List<AssistantMessage.ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return new CheckResult(true, 1.0, "无工具调用，跳过检查");
        }

        if (toolCalls.size() > 5) {
            return new CheckResult(false, 0.2,
                    "单次工具调用过多: " + toolCalls.size() + " 个（上限 5）");
        }

        boolean hasTerminate = toolCalls.stream()
                .anyMatch(tc -> "terminate".equals(tc.name()));
        if (hasTerminate && toolCalls.size() > 1) {
            return new CheckResult(false, 0.3,
                    "不应同时调用 terminate 和其他工具（共 " + toolCalls.size() + " 个调用）");
        }

        return new CheckResult(true, 1.0, "工具调用合理性检查通过");
    }

    /**
     * 一致性检查：
     * - 简单检查：如果上一条 Assistant 消息说"我不知道"，这一条又给出具体答案，标记为警告
     * - 目前简化处理，返回 PASS
     */
    private CheckResult checkConsistency(String outputText, List<Message> context) {
        // 目前简化处理，直接返回通过
        return new CheckResult(true, 1.0, "一致性检查通过（简化处理）");
    }

    /**
     * 生成修正提示词
     * 当验证失败时，生成一段提示文本注入到下一轮 think() 的上下文中
     *
     * @param result 验证结果
     * @return 修正提示词，验证通过时返回 null
     */
    public String generateCorrectionHint(VerificationResult result) {
        if (result.isPassed()) {
            return null;
        }

        StringBuilder hint = new StringBuilder();
        hint.append("【自验证反馈】");

        if (result.getStatus() == VerificationResult.Status.CRITICAL) {
            hint.append("上一次回答存在严重问题，请完全重新审视用户的问题。");
        } else {
            hint.append("上一次回答可能不够理想，请注意以下问题并改进：");
        }

        if (result.getIssues() != null && !result.getIssues().isEmpty()) {
            for (String issue : result.getIssues()) {
                hint.append("\n- ").append(issue);
            }
        }

        hint.append("\n请重新审视用户的问题并给出更针对性的回答。");

        return hint.toString();
    }

    // === 内部辅助 ===

    /** 单项检查结果 */
    private record CheckResult(boolean passed, double score, String detail) {}

    // === 验证结果类 ===

    /**
     * 验证结果
     */
    @Data
    @Builder
    public static class VerificationResult {

        public enum Status {
            /** 验证通过 */
            PASS,
            /** 需要重试 */
            NEEDS_RETRY,
            /** 严重问题 */
            CRITICAL
        }

        /** 验证状态 */
        private Status status;

        /** 原因描述 */
        private String reason;

        /** 发现的问题列表 */
        @Builder.Default
        private List<String> issues = new ArrayList<>();

        /** 信心分数 0.0-1.0 */
        private double confidenceScore;

        /** 是否通过 */
        public boolean isPassed() {
            return status == Status.PASS;
        }

        /** 创建通过结果 */
        public static VerificationResult pass() {
            return VerificationResult.builder()
                    .status(Status.PASS)
                    .reason("验证通过")
                    .issues(new ArrayList<>())
                    .confidenceScore(1.0)
                    .build();
        }

        /** 创建需重试结果 */
        public static VerificationResult needsRetry(String reason, List<String> issues) {
            return VerificationResult.builder()
                    .status(Status.NEEDS_RETRY)
                    .reason(reason)
                    .issues(issues != null ? new ArrayList<>(issues) : new ArrayList<>())
                    .confidenceScore(0.5)
                    .build();
        }

        /** 创建严重问题结果 */
        public static VerificationResult critical(String reason) {
            return VerificationResult.builder()
                    .status(Status.CRITICAL)
                    .reason(reason)
                    .issues(new ArrayList<>())
                    .confidenceScore(0.0)
                    .build();
        }
    }
}
