package com.kama.jchatmind.agent.harness.recovery;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 安全护栏 — L6 约束校验层
 * 对用户输入、工具调用、模型输出进行安全校验，防止注入攻击和敏感信息泄露
 */
@Slf4j
public class Guardrails {

    // ====== 输入校验相关常量 ======

    /** 用户消息最大长度 */
    private static final int MAX_INPUT_LENGTH = 10000;

    /** SQL 注入检测正则：匹配常见 SQL 注入模式（OR 1=1、UNION SELECT、注释符等） */
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            "(?i)(\\b(union\\s+select|select\\s+.*\\s+from|insert\\s+into|delete\\s+from|drop\\s+table|" +
            "alter\\s+table|truncate\\s+table|update\\s+.*\\s+set)\\b|" +
            "(--|;\\s*$|'\\s*(or|and)\\s+'?\\d*'?\\s*=\\s*'?\\d*'?|'\\s*(or|and)\\s+.*--))"
    );

    /** Prompt 注入检测正则：匹配常见 prompt 攻击模式 */
    private static final Pattern PROMPT_INJECTION_PATTERN = Pattern.compile(
            "(?i)(ignore\\s+(all\\s+)?previous\\s+instructions|" +
            "forget\\s+(all\\s+)?previous|" +
            "disregard\\s+(all\\s+)?(previous|above)|" +
            "you\\s+are\\s+now\\s+a|" +
            "new\\s+instructions?:|" +
            "system\\s*prompt:|" +
            "act\\s+as\\s+(if|a)|" +
            "pretend\\s+you\\s+are|" +
            "override\\s+(previous|your)|" +
            "do\\s+not\\s+follow\\s+(previous|your)|" +
            "reveal\\s+your\\s+(system|initial)\\s+prompt)"
    );

    // ====== 工具调用校验相关常量 ======

    /** 危险 SQL 关键字列表 */
    private static final List<String> DANGEROUS_SQL_KEYWORDS = List.of(
            "DELETE", "DROP", "TRUNCATE", "ALTER", "UPDATE"
    );

    /** 路径遍历检测正则 */
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile(
            "(\\.\\.[\\\\/]|\\.\\.%2[fF])"
    );

    /** 危险系统命令列表 */
    private static final List<String> DANGEROUS_COMMANDS = List.of(
            "rm", "rm -rf", "del", "format", "mkfs", "fdisk",
            "shutdown", "reboot", "kill", "taskkill", "rmdir"
    );

    // ====== 输出校验相关常量 ======

    /** 最大输出长度 */
    private static final int MAX_OUTPUT_LENGTH = 50000;

    /** API Key 泄露检测正则：匹配常见 API Key 格式 */
    private static final Pattern API_KEY_PATTERN = Pattern.compile(
            "(?i)(api[_-]?key|api[_-]?secret|access[_-]?token|secret[_-]?key|auth[_-]?token)" +
            "\\s*[:=]\\s*['\"]?[A-Za-z0-9+/=_\\-]{16,}['\"]?"
    );

    /** 密码泄露检测正则：匹配 password=xxx 等模式 */
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "(?i)(password|passwd|pwd)\\s*[:=]\\s*['\"]?[^\\s'\"]{4,}['\"]?"
    );

    // ============================
    //         输入校验
    // ============================

    /**
     * 校验用户输入消息
     * 检查消息长度、SQL 注入、Prompt 注入等风险
     *
     * @param userMessage 用户输入消息
     * @return 校验结果
     */
    public ValidationResult validateInput(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            log.warn("[Guardrails] 输入校验：收到空消息");
            return ValidationResult.reject("输入消息不能为空");
        }

        // 消息长度检查
        if (userMessage.length() > MAX_INPUT_LENGTH) {
            log.warn("[Guardrails] 输入校验：消息长度 {} 超过上限 {}", userMessage.length(), MAX_INPUT_LENGTH);
            return ValidationResult.reject("输入消息长度超过限制（最大 " + MAX_INPUT_LENGTH + " 字符）");
        }

        // SQL 注入模式检测
        if (SQL_INJECTION_PATTERN.matcher(userMessage).find()) {
            log.warn("[Guardrails] 输入校验：检测到疑似 SQL 注入模式");
            return ValidationResult.warn("输入中包含疑似 SQL 注入模式，请检查输入内容");
        }

        // Prompt 注入模式检测
        if (PROMPT_INJECTION_PATTERN.matcher(userMessage).find()) {
            log.warn("[Guardrails] 输入校验：检测到疑似 Prompt 注入攻击");
            return ValidationResult.reject("输入中包含疑似 Prompt 注入攻击模式");
        }

        log.debug("[Guardrails] 输入校验通过");
        return ValidationResult.pass();
    }

    // ============================
    //       工具调用校验
    // ============================

    /**
     * 校验工具调用参数
     * 检测危险 SQL 操作、路径遍历攻击、危险系统命令等
     *
     * @param toolName  工具名称
     * @param arguments 工具调用参数（JSON 字符串）
     * @return 校验结果
     */
    public ValidationResult validateToolCall(String toolName, String arguments) {
        if (toolName == null || toolName.isBlank()) {
            log.warn("[Guardrails] 工具校验：工具名称为空");
            return ValidationResult.reject("工具名称不能为空");
        }

        String upperArgs = (arguments != null) ? arguments.toUpperCase() : "";

        // 危险 SQL 关键字检测（如 DELETE / DROP / TRUNCATE / ALTER / UPDATE）
        for (String keyword : DANGEROUS_SQL_KEYWORDS) {
            if (upperArgs.contains(keyword)) {
                log.warn("[Guardrails] 工具校验：工具 [{}] 参数中包含危险 SQL 关键字 [{}]", toolName, keyword);
                return ValidationResult.reject("工具调用参数中包含危险 SQL 操作: " + keyword);
            }
        }

        // 路径遍历检测（../）
        if (arguments != null && PATH_TRAVERSAL_PATTERN.matcher(arguments).find()) {
            log.warn("[Guardrails] 工具校验：工具 [{}] 参数中检测到路径遍历模式", toolName);
            return ValidationResult.reject("工具调用参数中包含路径遍历攻击模式");
        }

        // 危险系统命令检测
        if (arguments != null) {
            String lowerArgs = arguments.toLowerCase();
            for (String cmd : DANGEROUS_COMMANDS) {
                // 匹配独立的命令词（前后为空白或字符串边界）
                if (lowerArgs.matches(".*\\b" + Pattern.quote(cmd) + "\\b.*")) {
                    log.warn("[Guardrails] 工具校验：工具 [{}] 参数中检测到危险命令 [{}]", toolName, cmd);
                    return ValidationResult.reject("工具调用参数中包含危险系统命令: " + cmd);
                }
            }
        }

        log.debug("[Guardrails] 工具校验通过：{}", toolName);
        return ValidationResult.pass();
    }

    // ============================
    //         输出校验
    // ============================

    /**
     * 校验模型输出内容
     * 检测 API Key / 密码等敏感信息泄露，以及输出长度
     *
     * @param assistantMessage 模型输出消息
     * @return 校验结果
     */
    public ValidationResult validateOutput(String assistantMessage) {
        if (assistantMessage == null || assistantMessage.isBlank()) {
            log.debug("[Guardrails] 输出校验：输出为空，跳过");
            return ValidationResult.pass();
        }

        // 输出长度检查
        if (assistantMessage.length() > MAX_OUTPUT_LENGTH) {
            log.warn("[Guardrails] 输出校验：输出长度 {} 超过上限 {}", assistantMessage.length(), MAX_OUTPUT_LENGTH);
            return ValidationResult.warn("输出内容过长（超过 " + MAX_OUTPUT_LENGTH + " 字符），建议精简");
        }

        // API Key 泄露检测
        if (API_KEY_PATTERN.matcher(assistantMessage).find()) {
            log.warn("[Guardrails] 输出校验：检测到疑似 API Key 泄露");
            return ValidationResult.reject("输出中包含疑似 API Key 等敏感信息");
        }

        // 密码泄露检测
        if (PASSWORD_PATTERN.matcher(assistantMessage).find()) {
            log.warn("[Guardrails] 输出校验：检测到疑似密码信息泄露");
            return ValidationResult.reject("输出中包含疑似密码等敏感信息");
        }

        log.debug("[Guardrails] 输出校验通过");
        return ValidationResult.pass();
    }

    // ============================
    //     校验结果内部类
    // ============================

    /**
     * 校验结果
     */
    public static class ValidationResult {

        /** 校验状态枚举 */
        public enum Status {
            /** 通过 */
            PASS,
            /** 警告（允许继续，但记录日志） */
            WARN,
            /** 拒绝（阻断执行） */
            REJECT
        }

        private final Status status;
        private final String reason;

        private ValidationResult(Status status, String reason) {
            this.status = status;
            this.reason = reason;
        }

        /** 创建「通过」结果 */
        public static ValidationResult pass() {
            return new ValidationResult(Status.PASS, "校验通过");
        }

        /** 创建「警告」结果 */
        public static ValidationResult warn(String reason) {
            return new ValidationResult(Status.WARN, reason);
        }

        /** 创建「拒绝」结果 */
        public static ValidationResult reject(String reason) {
            return new ValidationResult(Status.REJECT, reason);
        }

        /** 便捷方法：是否允许继续执行（PASS 和 WARN 都允许） */
        public boolean isAllowed() {
            return status != Status.REJECT;
        }

        public Status getStatus() {
            return status;
        }

        public String getReason() {
            return reason;
        }

        @Override
        public String toString() {
            return "ValidationResult{status=" + status + ", reason='" + reason + "'}";
        }
    }
}
