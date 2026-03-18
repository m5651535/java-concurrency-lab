package com.lab.nio.reactive.flux.service;

import com.lab.nio.reactive.flux.entity.User;
import com.lab.nio.reactive.flux.repository.UserRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@Slf4j
public class UserDbService {

    private final UserRepository userRepository;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    public UserDbService(UserRepository userRepository,
                         ReactiveRedisTemplate<String, Object> redisTemplate) {
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
    }

    @CircuitBreaker(name = "dbBreaker", fallbackMethod = "fetchFallback")
    public Mono<User> fetchAndCache(Long id, String key) {
        return userRepository.findById(id)
                .timeout(Duration.ofSeconds(1))
                .switchIfEmpty(Mono.error(
                        new RuntimeException("User not found: " + id)
                ))
                .flatMap(user ->
                        redisTemplate.opsForValue()
                                .set(key, user, Duration.ofMinutes(10))
                                .thenReturn(user)
                );
    }

    public Mono<User> fetchFallback(Long id, String key, Throwable e) {
        log.warn("Circuit breaker triggered for user {}: {}", id, e.getMessage());
        return Mono.just(new User(id, "System Busy", "retry.later@example.com"));
    }
}
