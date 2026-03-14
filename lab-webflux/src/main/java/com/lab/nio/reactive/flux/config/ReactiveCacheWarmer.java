package com.lab.nio.reactive.flux.config;

import com.lab.nio.reactive.flux.constants.CacheConstants;
import com.lab.nio.reactive.flux.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Slf4j
@RequiredArgsConstructor
public class ReactiveCacheWarmer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        log.info("🚀 開始執行響應式緩存預熱...");

        // 模擬熱點資料預熱：撈取前 100 筆使用者資料
        userRepository.findAll()
                .take(100)
                .flatMap(user -> {
                    String key = CacheConstants.USER_PREFIX + user.getId();
                    return redisTemplate.opsForValue()
                            .set(key, user, Duration.ofMinutes(30))
                            .doOnSuccess(v -> log.debug("預熱成功: {}", key));
                })
                .then() // 等待所有預熱動作完成 (但不阻塞主執行緒)
                .doOnSuccess(v -> log.info("✅ 緩存預熱完成，系統已準備好迎接高併發流量！"))
                .doOnError(error -> log.error("❌ 預熱過程中發生異常: {}", error.getMessage()))
                .subscribe(); // 僅作為觸發開關，不帶任何參數
    }
}