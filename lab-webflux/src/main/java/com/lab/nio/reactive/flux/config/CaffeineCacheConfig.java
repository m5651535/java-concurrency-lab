package com.lab.nio.reactive.flux.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lab.nio.reactive.flux.entity.User;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CaffeineCacheConfig {

    @Bean
    public Cache<String, User> caffeineCache() {
        return Caffeine.newBuilder()
                .maximumSize(500)               // 最多快取 500 筆，超過自動 LRU 淘汰
                .expireAfterWrite(Duration.ofMinutes(5)) // L1 TTL 比 Redis (10min) 短，避免髒資料
                .recordStats()                  // 開啟命中率統計，方便之後用 actuator 觀察
                .build();
    }
}