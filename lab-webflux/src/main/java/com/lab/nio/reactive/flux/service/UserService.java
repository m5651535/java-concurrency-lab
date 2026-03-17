package com.lab.nio.reactive.flux.service;

import com.lab.nio.reactive.flux.constants.CacheConstants;
import com.lab.nio.reactive.flux.entity.User;
import com.lab.nio.reactive.flux.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLockReactive;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final RedissonReactiveClient redissonReactiveClient; // 👈 注入反應式客戶端

    public Mono<User> getUserById(Long id) {
        String key = CacheConstants.USER_PREFIX + id;
        String lockKey = "lock:user:" + id;

        // 1. 第一層防護：直接查快取
        return redisTemplate.opsForValue().get(key)
                .cast(User.class)
                .onErrorResume(e -> {
                    log.warn("Redis 讀取異常: {}", e.getMessage());
                    return Mono.empty();
                })
                // 2. 快取失效，進入「保鏢模式」
                .switchIfEmpty(Mono.defer(() -> handleCacheMissWithLock(id, key, lockKey)));
    }

    private Mono<User> handleCacheMissWithLock(Long id, String key, String lockKey) {
        RLockReactive lock = redissonReactiveClient.getLock(lockKey);

        // 3. 嘗試獲取非阻塞鎖 (最多等 2s, 5s 後自動釋放)
        return lock.tryLock(2, 5, TimeUnit.SECONDS)
                .flatMap(acquired -> {
                    if (acquired) {
                        // 4. [關鍵] Double Check：拿到鎖後再查一次快取，可能前一個人剛填好
                        return redisTemplate.opsForValue().get(key)
                                .cast(User.class)
                                .switchIfEmpty(
                                        // 5. 真的沒人填，才去煩資料庫
                                        userRepository.findById(id)
                                                .flatMap(user ->
                                                        redisTemplate.opsForValue()
                                                                .set(key, user, Duration.ofMinutes(10))
                                                                .thenReturn(user)
                                                )
                                )
                                // 6. 最終一定要釋放鎖 (即便發生錯誤)
                                .doFinally(signalType -> lock.unlock().subscribe());
                    } else {
                        // 7. 沒搶到鎖的人：延遲 100ms 後重試 (這時快取通常已經被搶到鎖的人填好了)
                        return Mono.delay(Duration.ofMillis(100))
                                .then(getUserById(id));
                    }
                });
    }
}