package com.lab.nio.reactive.mvc.service;

import com.lab.nio.reactive.mvc.entity.User;
import com.lab.nio.reactive.mvc.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private static final String CACHE_KEY_PREFIX = "lab:user:";

    public User getUserById(Long id) {
        String key = CACHE_KEY_PREFIX + id;

        // 1. 嘗試從 Redis 獲取 (阻塞式呼叫，但在虛擬執行緒下很輕量)
        User cachedUser = (User) redisTemplate.opsForValue().get(key);
        if (cachedUser != null) {
            log.debug("Cache Hit: {}", key);
            return cachedUser;
        }

        // 2. Cache Miss, 從 PostgreSQL 獲取
        log.warn("Cache Miss: {}, fetching from DB", key);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 3. 回填 Redis 並設置 30 分鐘過期
        redisTemplate.opsForValue().set(key, user, Duration.ofMinutes(30));

        return user;
    }
}