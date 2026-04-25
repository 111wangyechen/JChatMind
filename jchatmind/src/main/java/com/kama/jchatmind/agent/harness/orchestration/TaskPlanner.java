package com.kama.jchatmind.agent.harness.orchestration;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 任务规划器
 * L3 执行编排层组件，负责将复杂的用户请求分解为可执行的步骤计划
 */
@Slf4j
public class TaskPlanner {

    private final ChatClient chatClient;

    /** 复杂任务关键词列表 */
    private static final List<String> COMPLEX_KEYWORDS = List.of(
            "帮我", "请", "步骤", "然后", "接着", "首先", "分析", "对比",
            "比较", "总结", "整理", "分别", "依次", "逐个", "最后"
    );

    public TaskPlanner(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 判断是否需要进行任务规划
     *
     * @param userMessage 用户消息
     * @return 是否需要规划
     */
    public boolean shouldPlan(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }

        // 消息长度 > 50 字符
        if (userMessage.length() > 50) {
            return true;
        }

        // 包含复杂任务关键词
        for (String keyword : COMPLEX_KEYWORDS) {
            if (userMessage.contains(keyword)) {
                return true;
            }
        }

        // 包含多个问号
        long questionMarkCount = userMessage.chars().filter(c -> c == '?' || c == '？').count();
        if (questionMarkCount > 1) {
            return true;
        }

        // 包含多个句子（通过句号、分号等判断）
        long sentenceEndCount = userMessage.chars()
                .filter(c -> c == '。' || c == '；' || c == '.' || c == ';')
                .count();
        if (sentenceEndCount > 1) {
            return true;
        }

        return false;
    }

    /**
     * 生成执行计划
     *
     * @param userMessage        用户消息
     * @param availableToolNames 可用工具名称列表
     * @return 执行计划
     */
    public ExecutionPlan plan(String userMessage, List<String> availableToolNames) {
        log.info("开始生成执行计划，用户消息: {}...",
                userMessage.substring(0, Math.min(50, userMessage.length())));

        String promptText = """
                你是一个任务规划器。请将以下用户请求分解为清晰的执行步骤。
                
                可用工具：%s
                
                用户请求：%s
                
                请以JSON格式输出执行计划：
                {"steps": [{"step": 1, "action": "描述", "expectedTool": "工具名或null"}]}
                """.formatted(availableToolNames, userMessage);

        try {
            String response = chatClient.prompt()
                    .user(promptText)
                    .call()
                    .content();

            log.debug("LLM 规划响应: {}", response);

            List<PlanStep> steps = parseSteps(response);
            ExecutionPlan plan = new ExecutionPlan();
            plan.setSteps(steps);
            plan.setRawPlanText(response);

            log.info("执行计划生成完成，共 {} 个步骤", steps.size());
            return plan;
        } catch (Exception e) {
            log.error("生成执行计划失败: {}", e.getMessage(), e);
            // 返回一个包含单步骤的降级计划
            ExecutionPlan fallbackPlan = new ExecutionPlan();
            fallbackPlan.setSteps(List.of(new PlanStep(1, "直接处理用户请求", null)));
            fallbackPlan.setRawPlanText("规划失败，使用降级方案");
            return fallbackPlan;
        }
    }

    /**
     * 从 LLM 响应中解析执行步骤（简单字符串提取，容错处理）
     */
    private List<PlanStep> parseSteps(String response) {
        List<PlanStep> steps = new ArrayList<>();
        if (response == null || response.isBlank()) {
            return steps;
        }

        try {
            // 使用正则提取每个步骤
            Pattern stepPattern = Pattern.compile(
                    "\"step\"\\s*:\\s*(\\d+)\\s*,\\s*\"action\"\\s*:\\s*\"([^\"]*?)\"\\s*,\\s*\"expectedTool\"\\s*:\\s*(?:\"([^\"]*?)\"|null)"
            );
            Matcher matcher = stepPattern.matcher(response);

            while (matcher.find()) {
                int step = Integer.parseInt(matcher.group(1));
                String action = matcher.group(2);
                String expectedTool = matcher.group(3); // 可能为 null
                steps.add(new PlanStep(step, action, expectedTool));
            }
        } catch (Exception e) {
            log.warn("解析执行计划步骤失败，原始响应: {}", response, e);
        }

        // 如果解析失败，至少返回一个默认步骤
        if (steps.isEmpty()) {
            steps.add(new PlanStep(1, "处理用户请求", null));
        }

        return steps;
    }

    /**
     * 执行计划
     */
    @Data
    public static class ExecutionPlan {
        /** 执行步骤列表 */
        private List<PlanStep> steps = new ArrayList<>();

        /** LLM 原始输出 */
        private String rawPlanText;

        /**
         * 返回计划摘要文本
         */
        public String getSummary() {
            if (steps.isEmpty()) {
                return "无执行计划";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("执行计划（共 ").append(steps.size()).append(" 步）：\n");
            for (PlanStep step : steps) {
                sb.append("  步骤 ").append(step.step()).append(": ").append(step.action());
                if (step.expectedTool() != null) {
                    sb.append(" [工具: ").append(step.expectedTool()).append("]");
                }
                sb.append("\n");
            }
            return sb.toString();
        }
    }

    /**
     * 计划步骤
     */
    public record PlanStep(int step, String action, String expectedTool) {
    }
}
