package com.lab.nio.reactive.mvc.config;

import com.lab.nio.reactive.mvc.entity.User;
import com.lab.nio.reactive.mvc.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executors;

@Component
@Slf4j
@RequiredArgsConstructor
public class MvcCacheWarmer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        log.info("🚀 [MVC] 開始執行虛擬執行緒緩存預熱...");

        List<User> hotUsers = userRepository.findAll().stream().limit(100).toList();

        // 關鍵：使用 Java 21 虛擬執行緒執行器
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (User user : hotUsers) {
                executor.submit(() -> {
                    String key = "lab:user:" + user.getId();
                    redisTemplate.opsForValue().set(key, user);
                    log.debug("[MVC] 預熱成功: {}", key);
                });
            }
        } // executor 會在此自動關閉並等待所有預熱任務完成

        log.info("✅ [MVC] 緩存預熱完成！已利用虛擬執行緒併發填充 100 筆資料。");
    }
}