package com.kama.jchatmind.agent.harness;

import lombok.Builder;
import lombok.Data;

/**
 * Harness Engineering 配置类
 * 控制六层 Harness 各项参数和开关
 */
@Data
@Builder
public class HarnessConfig {

    // === L1: 信息边界层配置 ===
    /** 最大 Token 预算（默认 4096） */
    @Builder.Default
    private int maxTokenBudget = 4096;

    /** 上下文压缩触发阈值（占比，默认 0.4 即 40%） */
    @Builder.Default
    private double contextCompressionThreshold = 0.4;

    /** 强制压缩阈值（默认 0.7 即 70%） */
    @Builder.Default
    private double contextCriticalThreshold = 0.7;

    // === L2: 工具系统层配置 ===
    /** 工具结果最大长度（超过则压缩，默认 2000 字符） */
    @Builder.Default
    private int toolResultMaxLength = 2000;

    // === L3: 执行编排层配置 ===
    /** 是否启用任务规划（默认 true） */
    @Builder.Default
    private boolean enableTaskPlanning = true;

    // === L5: 评估层配置 ===
    /** 是否启用输出自验证（默认 true） */
    @Builder.Default
    private boolean enableSelfVerification = true;

    /** 自验证最大重试次数（默认 2） */
    @Builder.Default
    private int maxVerificationRetries = 2;

    // === L6: 恢复层配置 ===
    /** 是否启用安全护栏（默认 true） */
    @Builder.Default
    private boolean enableGuardrails = true;

    /** 最大重试次数（默认 3） */
    @Builder.Default
    private int maxRetries = 3;

    /** 重试基础延迟毫秒（默认 1000） */
    @Builder.Default
    private long retryBackoffMs = 1000;
}
