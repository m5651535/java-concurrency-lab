package com.lab.nio.reactive.flux.controller;

import com.lab.nio.reactive.flux.entity.User;
import com.lab.nio.reactive.flux.repository.UserRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

@RestController
@RequestMapping("/flux")
public class FluxLabController {
    private static final Logger log = LoggerFactory.getLogger(FluxLabController.class);
    private final UserRepository userRepository;

    public FluxLabController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // 1. 觀察 EventLoop (快速響應)
    @GetMapping("/fast")
    public Mono<String> fast() {
        return Mono.just("OK").delayElement(Duration.ofMillis(100));
    }

    // 2. 錯誤示範：阻塞 EventLoop (雪崩測試)
    @GetMapping("/block-bad")
    public Mono<String> blockBad() {
        return Mono.fromCallable(() -> {
            Thread.sleep(5000);
            return "Blocked result";
        });
    }

    // 3. 正確做法：執行緒切換 (Offloading)
    @GetMapping("/block-good")
    public Mono<String> blockGood() {
        return Mono.fromCallable(() -> {
            Thread.sleep(5000);
            return "Safe result";
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // 4. R2DBC 串流輸出 (NDJSON)
    @GetMapping(value = "/db-stream", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<User> dbStream() {
        return userRepository.findAll().delayElements(Duration.ofSeconds(1));
    }

    // 5. SSE 打字機效果
    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat() {
        String msg = "你好！這是一個利用 WebFlux 實現的非阻塞打字機效果...";
        return Flux.fromArray(msg.split("")).delayElements(Duration.ofMillis(50));
    }

    @GetMapping("/test")
    public Mono<String> test() {
        return Mono.delay(Duration.ofSeconds(1)) // 這是「非阻塞延遲」，執行緒會先去幫別人做事
                .thenReturn("Flux Done");
    }

    @GetMapping("/user/{id}")
    @CircuitBreaker(name = "dbBreaker", fallbackMethod = "dbFallback")
    public Mono<User> getUser(@PathVariable Long id) {
        return userRepository.findById(id)
                .timeout(Duration.ofSeconds(2)); // 只管拋出 TimeoutException，不要 onErrorResume
    }

    // WebFlux 的 Fallback 必須回傳 Mono/Flux
    public Mono<User> dbFallback(Long id, Throwable e) {
        return Mono.just(new User(id, "System Busy (Flux Fallback)", "retry.later@example.com"));
    }
}