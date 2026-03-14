package com.lab.nio.reactive.flux.service;

import com.lab.nio.reactive.flux.constants.CacheConstants;
import com.lab.nio.reactive.flux.entity.User;
import com.lab.nio.reactive.flux.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    public Mono<User> getUserById(Long id) {
        String key = CacheConstants.USER_PREFIX + id;

        return redisTemplate.opsForValue().get(key)
                .cast(User.class)
                // --- 核心故障處理 ---
                .onErrorResume(e -> {
                    log.warn("Redis 故障，改由 DB 取得資料. Error: {}", e.getMessage());
                    return Mono.empty(); // 讓流程繼續走向 switchIfEmpty
                })
                // --------------------
                .switchIfEmpty(
                        userRepository.findById(id)
                                .flatMap(user ->
                                        redisTemplate.opsForValue()
                                                .set(key, user, Duration.ofMinutes(10))
                                                .onErrorResume(ex -> Mono.just(true)) // 寫入 Redis 失敗也沒關係，不影響回傳
                                                .thenReturn(user)
                                )
                );
    }
}
