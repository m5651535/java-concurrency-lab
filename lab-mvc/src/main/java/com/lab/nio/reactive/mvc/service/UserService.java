package com.lab.nio.reactive.mvc.service;

import com.lab.nio.reactive.mvc.entity.User;
import com.lab.nio.reactive.mvc.repository.UserRepository;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;

    private static final String CACHE_KEY_PREFIX = "lab:user:";
    private static final String LOCK_PREFIX = "lock:user:";

    @Value("${app.feature.double-delete-enabled:true}")
    private boolean isDoubleDeleteEnabled;

    @Value("${app.feature.double-delete-delay-ms:500}")
    private long doubleDeleteDelay;

    private final ObservationRegistry observationRegistry;

    public User getUserSimple(Long id) {
        return Observation.createNotStarted("getUserSimple.logic", observationRegistry).observe(() -> {
            String key = CACHE_KEY_PREFIX + id;
            User cachedUser = (User) redisTemplate.opsForValue().get(key);

            if (cachedUser != null) {
                return cachedUser;
            }

            // --- 為了實驗效果，人為製造擊穿窗口 ---
            // TODO: For experiment simulation
//        try {
//            // 模擬一個稍微慢一點的查詢，讓後面的 1000 個人有機會衝進來
//            Thread.sleep(200);
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//        }
            // ---------------------------------

            log.warn("🔥 擊穿發生！Cache Miss: {}, fetching from DB", key);
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            redisTemplate.opsForValue().set(key, user, Duration.ofMinutes(30));
            return user;
                });

    }

    /**
     * 更新用戶：使用延遲雙刪策略
     */
    public void updateUser(User user) {
        String key = CACHE_KEY_PREFIX + user.getId();

        // 1. 第一次刪除：減少更新期間的髒數據讀取
        redisTemplate.delete(key);
        log.info("🛡️ [Phase 1] 第一次快取刪除: {}", key);

        // 2. 更新資料庫
        userRepository.save(user);
        log.info("💾 [Phase 2] 資料庫更新完成: {}", user.getUsername());

        // 3. 延遲雙刪邏輯
        if (isDoubleDeleteEnabled) {
            Thread.ofVirtual().start(() -> {
                try {
                    Thread.sleep(doubleDeleteDelay);
                    redisTemplate.delete(key);
                    log.info("🚀 [Phase 3] 延遲雙刪完成 (Virtual Thread)，已清理髒數據回填: {}", key);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("❌ 延遲雙刪執行緒中斷", e);
                }
            });
        } else {
            log.warn("⚠️ [Skip] 延遲雙刪已關閉，系統處於髒數據重現模式");
        }
    }

    /**
     * 讀取用戶：使用分散式鎖防止快取擊穿
     */
    public User getUserWithLock(Long id) {
        String key = CACHE_KEY_PREFIX + id;

        // 1. 先查快取
        User user = (User) redisTemplate.opsForValue().get(key);
        if (user != null) return user;

        // 2. 快取失效，準備搶鎖查 DB
        String lockKey = LOCK_PREFIX + id;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 嘗試獲取鎖，最多等 2 秒，獲取後 5 秒自動釋放
            if (lock.tryLock(2, 5, TimeUnit.SECONDS)) {
                try {
                    // Double Check: 拿到鎖後再查一次快取，可能別人剛填好
                    user = (User) redisTemplate.opsForValue().get(key);
                    if (user != null) return user;

                    // 查資料庫
                    user = userRepository.findById(id).orElse(null);

                    if (user != null) {
                        // 回填快取，設置過期時間防止擊穿
                        redisTemplate.opsForValue().set(key, user, 30, TimeUnit.MINUTES);
                    }
                } finally {
                    lock.unlock(); // 釋放鎖
                }
            } else {
                // 沒搶到鎖的請求：稍微等待後重試讀取快取（通常由搶到鎖的人回填了）
                Thread.sleep(100);
                return getUserWithLock(id);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return user;
    }
}