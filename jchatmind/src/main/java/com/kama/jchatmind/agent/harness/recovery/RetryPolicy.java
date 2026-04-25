package com.kama.jchatmind.agent.harness.recovery;

import lombok.extern.slf4j.Slf4j;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 重试策略 — L6 恢复层
 * 提供指数退避重试机制，自动判断异常是否可重试
 */
@Slf4j
public class RetryPolicy {

    /** 最大重试次数 */
    private final int maxRetries;

    /** 重试基础延迟（毫秒） */
    private final long baseDelayMs;

    /** 延迟上限（毫秒） */
    private static final long MAX_DELAY_MS = 30000;

    /** 最大随机抖动（毫秒） */
    private static final long MAX_JITTER_MS = 500;

    /** 不可重试的异常类型集合（逻辑错误不应重试） */
    private static final Set<Class<? extends Exception>> NON_RETRYABLE_EXCEPTIONS = Set.of(
            NullPointerException.class,
            IllegalArgumentException.class,
            IllegalStateException.class,
            ClassCastException.class,
            IndexOutOfBoundsException.class,
            UnsupportedOperationException.class
    );

    /**
     * @param maxRetries  最大重试次数
     * @param baseDelayMs 基础延迟毫秒数
     */
    public RetryPolicy(int maxRetries, long baseDelayMs) {
        this.maxRetries = maxRetries;
        this.baseDelayMs = baseDelayMs;
    }

    /**
     * 判断当前异常在当前尝试次数下是否应重试
     *
     * @param exception      发生的异常
     * @param currentAttempt 当前已尝试次数（从 1 开始）
     * @return 是否应重试
     */
    public boolean shouldRetry(Exception exception, int currentAttempt) {
        // 超过最大重试次数，不再重试
        if (currentAttempt >= maxRetries) {
            log.debug("[RetryPolicy] 已达最大重试次数 {}，不再重试", maxRetries);
            return false;
        }

        // 不可重试的逻辑异常
        if (NON_RETRYABLE_EXCEPTIONS.contains(exception.getClass())) {
            log.debug("[RetryPolicy] 异常类型 {} 不可重试", exception.getClass().getSimpleName());
            return false;
        }

        // 可重试：网络超时类异常
        if (exception instanceof SocketTimeoutException || exception instanceof ConnectException) {
            log.debug("[RetryPolicy] 网络异常，可重试: {}", exception.getClass().getSimpleName());
            return true;
        }

        // 可重试：通过异常消息匹配 HTTP 429（限流）和 503（服务不可用）
        String message = exception.getMessage();
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            if (lowerMessage.contains("429") || lowerMessage.contains("too many requests") ||
                lowerMessage.contains("rate limit")) {
                log.debug("[RetryPolicy] 检测到 API 限流 (429)，可重试");
                return true;
            }
            if (lowerMessage.contains("503") || lowerMessage.contains("service unavailable")) {
                log.debug("[RetryPolicy] 检测到服务不可用 (503)，可重试");
                return true;
            }
            // Spring AI 相关临时异常
            if (lowerMessage.contains("timeout") || lowerMessage.contains("timed out") ||
                lowerMessage.contains("connection reset") || lowerMessage.contains("connection refused")) {
                log.debug("[RetryPolicy] 检测到临时性连接异常，可重试");
                return true;
            }
        }

        // 默认：未知异常允许重试（保守策略）
        log.debug("[RetryPolicy] 未知异常类型 {}，默认允许重试", exception.getClass().getSimpleName());
        return true;
    }

    /**
     * 计算当前重试的延迟时间（指数退避 + 随机抖动）
     * delay = baseDelayMs * 2^(attempt - 1) + jitter，上限 30s
     *
     * @param currentAttempt 当前尝试次数（从 1 开始）
     * @return 延迟毫秒数
     */
    public long getDelay(int currentAttempt) {
        // 指数退避：baseDelayMs * 2^(attempt-1)
        long exponentialDelay = baseDelayMs * (1L << (currentAttempt - 1));

        // 添加随机抖动（0 ~ MAX_JITTER_MS）
        long jitter = ThreadLocalRandom.current().nextLong(0, MAX_JITTER_MS + 1);

        // 计算总延迟并应用上限
        long totalDelay = Math.min(exponentialDelay + jitter, MAX_DELAY_MS);

        log.debug("[RetryPolicy] 第 {} 次重试延迟: {}ms (指数退避 {}ms + 抖动 {}ms)",
                currentAttempt, totalDelay, exponentialDelay, jitter);
        return totalDelay;
    }

    /**
     * 通用重试执行器
     * 执行给定操作，失败时根据重试策略自动重试
     *
     * @param action 要执行的操作
     * @param <T>    返回值类型
     * @return 操作返回值
     * @throws RuntimeException 所有重试失败后抛出最后一次异常
     */
    public <T> T executeWithRetry(Supplier<T> action) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            try {
                return action.get();
            } catch (Exception e) {
                lastException = e;
                log.warn("[RetryPolicy] 第 {} 次执行失败: {}", attempt, e.getMessage());

                if (!shouldRetry(e, attempt)) {
                    log.error("[RetryPolicy] 异常不可重试或已达重试上限，终止重试");
                    break;
                }

                long delay = getDelay(attempt);
                log.info("[RetryPolicy] 将在 {}ms 后进行第 {} 次重试", delay, attempt + 1);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("[RetryPolicy] 重试等待被中断");
                    break;
                }
            }
        }

        // 所有重试失败，抛出最后一次异常
        throw new RuntimeException("所有重试均失败（共 " + maxRetries + " 次重试）", lastException);
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public long getBaseDelayMs() {
        return baseDelayMs;
    }
}
