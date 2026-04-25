package com.kama.jchatmind.agent.harness.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具结果提炼器
 * L2 工具系统层组件，负责对过长的工具调用结果进行压缩摘要
 */
@Slf4j
public class ToolResultSummarizer {

    /** 工具结果最大长度，超过则进行压缩 */
    private final int maxResultLength;

    public ToolResultSummarizer(int maxResultLength) {
        this.maxResultLength = maxResultLength;
    }

    /**
     * 判断是否需要压缩
     *
     * @param responseData 工具返回的原始数据
     * @return 如果长度超过阈值则返回 true
     */
    public boolean shouldSummarize(String responseData) {
        return responseData != null && responseData.length() > maxResultLength;
    }

    /**
     * 压缩工具结果
     *
     * @param toolName     工具名称
     * @param responseData 工具返回的原始数据
     * @return 压缩后的字符串
     */
    public String summarize(String toolName, String responseData) {
        if (responseData == null) {
            return null;
        }
        if (!shouldSummarize(responseData)) {
            return responseData;
        }

        log.debug("工具 {} 的结果超长（{}字符），进行压缩，阈值为 {} 字符",
                toolName, responseData.length(), maxResultLength);

        // 针对不同工具类型使用不同策略
        if (isSqlTableResult(responseData)) {
            return summarizeSqlResult(responseData);
        }
        if (isKnowledgeToolResult(toolName)) {
            return truncateWithSuffix(responseData, maxResultLength, "...[已截断]");
        }

        // 其他工具：保留前 maxResultLength 字符
        return truncateWithSuffix(responseData, maxResultLength, "...[已截断]");
    }

    /**
     * 处理完整的 ToolResponseMessage
     * 注意：不修改原 ToolResponseMessage（Spring AI 的 Message 是不可变的），
     * 返回一个包含压缩后内容的 Map（toolName → summarizedContent），供上下文注入使用
     *
     * @param toolResponseMessage 工具响应消息
     * @return 工具名到压缩后内容的映射
     */
    public Map<String, String> summarizeToolResponse(ToolResponseMessage toolResponseMessage) {
        Map<String, String> summarizedMap = new LinkedHashMap<>();
        if (toolResponseMessage == null || toolResponseMessage.getResponses() == null) {
            return summarizedMap;
        }

        for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
            String toolName = response.name();
            String responseData = response.responseData();
            String summarized = summarize(toolName, responseData);
            summarizedMap.put(toolName, summarized);
        }

        return summarizedMap;
    }

    /**
     * 判断是否为 SQL/数据库表格格式的结果（包含 | 表格格式）
     */
    private boolean isSqlTableResult(String responseData) {
        return responseData.contains("|") && responseData.contains("\n");
    }

    /**
     * 判断是否为知识库工具的结果
     */
    private boolean isKnowledgeToolResult(String toolName) {
        return toolName != null && toolName.toLowerCase().contains("knowledge");
    }

    /**
     * 压缩 SQL 表格结果：保留表头和前 10 行
     */
    private String summarizeSqlResult(String responseData) {
        String[] lines = responseData.split("\n");
        int totalLines = lines.length;

        // 保留表头（前 2 行，通常是列名和分隔线）和前 10 行数据
        int keepLines = Math.min(12, totalLines); // 2（表头）+ 10（数据行）
        if (totalLines <= keepLines) {
            return responseData;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keepLines; i++) {
            sb.append(lines[i]).append("\n");
        }
        sb.append("...[共 ").append(totalLines).append(" 行，已截断]");

        return sb.toString();
    }

    /**
     * 截断字符串并添加后缀
     */
    private String truncateWithSuffix(String text, int maxLength, String suffix) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + suffix;
    }
}
