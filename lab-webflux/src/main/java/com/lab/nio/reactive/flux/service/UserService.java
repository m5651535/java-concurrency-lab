package com.lab.nio.reactive.flux.service;

import com.lab.nio.reactive.flux.constants.CacheConstants;
import com.lab.nio.reactive.flux.entity.User;
import com.lab.nio.reactive.flux.repository.UserRepository;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
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

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final RedissonReactiveClient redissonReactiveClient;
    private final UserDbService userDbService;

    public Mono<User> getUserById(Long id) {
        String key = CacheConstants.USER_PREFIX + id;
        String lockKey = "lock:user:" + id;

        return redisTemplate.opsForValue().get(key)
                .cast(User.class)
                .switchIfEmpty(Mono.defer(() -> handleCacheMissWithLock(id, key, lockKey)));
    }

    private Mono<User> handleCacheMissWithLock(Long id, String key, String lockKey) {
        RLockReactive lock = redissonReactiveClient.getLock(lockKey);

        return lock.tryLock(2, 5, TimeUnit.SECONDS)
                .flatMap(acquired -> {
                    if (acquired) {
                        return redisTemplate.opsForValue().get(key)
                                .cast(User.class)
                                .switchIfEmpty(Mono.defer(() -> userDbService.fetchAndCache(id, key)))  // ← 跨 Bean 呼叫，AOP 生效
                                .flatMap(user -> lock.unlock().thenReturn(user))
                                .onErrorResume(e -> lock.unlock().then(Mono.error(e)));
                    } else {
                        return Mono.delay(Duration.ofMillis(100 + (int)(Math.random() * 50)))
                                .then(Mono.defer(() -> getUserById(id)));
                    }
                });
    }

}