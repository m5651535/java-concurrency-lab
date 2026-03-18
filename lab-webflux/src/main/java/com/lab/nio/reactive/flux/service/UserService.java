package com.lab.nio.reactive.flux.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.lab.nio.reactive.flux.constants.CacheConstants;
import com.lab.nio.reactive.flux.entity.User;
import com.lab.nio.reactive.flux.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLockReactive;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class UserService {

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final RedissonReactiveClient redissonReactiveClient;
    private final UserDbService userDbService;
    private final Cache<String, User> caffeineCache;
    private final UserRepository userRepository;

    public UserService(ReactiveRedisTemplate<String, Object> redisTemplate,
                       RedissonReactiveClient redissonReactiveClient,
                       UserDbService userDbService,
                       Cache<String, User> caffeineCache,
                       UserRepository userRepository) {
        this.redisTemplate = redisTemplate;
        this.redissonReactiveClient = redissonReactiveClient;
        this.userDbService = userDbService;
        this.caffeineCache = caffeineCache;
        this.userRepository = userRepository;
    }

    public Mono<User> getUserById(Long id) {
        String key = CacheConstants.USER_PREFIX + id;
        String lockKey = "lock:user:" + id;

        // L1: Caffeine（純 in-memory，無網路 RTT）
        return Mono.justOrEmpty(caffeineCache.getIfPresent(key))
                .switchIfEmpty(
                        // L2: Redis
                        redisTemplate.opsForValue().get(key)
                                .cast(User.class)
                                .doOnNext(user -> caffeineCache.put(key, user)) // 回填 L1
                )
                .switchIfEmpty(Mono.defer(() -> handleCacheMissWithLock(id, key, lockKey)));
    }

    private Mono<User> handleCacheMissWithLock(Long id, String key, String lockKey) {
        RLockReactive lock = redissonReactiveClient.getLock(lockKey);

        return lock.tryLock(2, 5, TimeUnit.SECONDS)
                .flatMap(acquired -> {
                    if (acquired) {
                        // Double Check：拿到鎖後再從 L1 → L2 查一次
                        // 前一個人剛填完，這裡就能直接命中，不用再打 DB
                        return Mono.justOrEmpty(caffeineCache.getIfPresent(key))
                                .switchIfEmpty(
                                        redisTemplate.opsForValue().get(key)
                                                .cast(User.class)
                                                .doOnNext(user -> caffeineCache.put(key, user))
                                )
                                .switchIfEmpty(Mono.defer(() -> userDbService.fetchAndCache(id, key)))
                                .flatMap(user -> lock.unlock().thenReturn(user))
                                .onErrorResume(e -> lock.unlock().then(Mono.error(e)));
                    } else {
                        return Mono.delay(Duration.ofMillis(100 + (int)(Math.random() * 50)))
                                .then(Mono.defer(() -> getUserById(id)));
                    }
                });
    }

    /**
     * 更新時同時清除 L1 + L2
     * 注意：單機清 L1 即可，若是多實例部署需要搭配 Pub/Sub 廣播
     */
    public Mono<User> updateUser(User user) {
        String key = CacheConstants.USER_PREFIX + user.getId();

        return redisTemplate.delete(key)
                .doOnSuccess(v -> caffeineCache.invalidate(key))
                .then(userRepository.save(user));
    }
}