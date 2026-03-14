package com.lab.nio.reactive.flux.constants;

public class CacheConstants {
    // 使用冒號分層，在 Redis GUI 工具（如 Redis Insight）中會自動變資料夾結構
    public static final String USER_PREFIX = "lab:user:";

    // 也可以順便定義預設的過期時間
    public static final java.time.Duration DEFAULT_TTL = java.time.Duration.ofMinutes(10);
}
