package com.lab.nio.reactive.mvc.controller;

import com.lab.nio.reactive.mvc.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/mvc")
public class MvcLabController {
    private static final Logger log = LoggerFactory.getLogger(MvcLabController.class);
    // 假設你使用的是 PostgreSQL 或 H2
    private final UserRepository userRepository;

    // [核心保護] 限制同時存取資料庫的「虛擬執行緒」數量
    // 雖然連線池只有 10 個，我們設 20~30 個許可證作為緩衝排隊
    private final Semaphore dbLimit = new Semaphore(200);

    public MvcLabController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // 實驗：傳統阻塞式 (配合 Virtual Threads 觀察)
    @GetMapping("/blocking")
    public String blocking() {
        log.info("Blocking task on: {}", Thread.currentThread());
        try { Thread.sleep(2000); } catch (InterruptedException e) {}
        return "Blocking Style Done";
    }

    // 實驗：10 萬個虛擬執行緒爆發
    @GetMapping("/spawn-100k")
    public String spawn100k() {
        AtomicInteger completedTasks = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 100_000; i++) {
                executor.submit(() -> {
                    try {
                        Thread.sleep(1000); // 模擬 IO
                        completedTasks.incrementAndGet();
                    } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                });
            }
        }

        return String.format("MVC 模式：完成 %d 個任務，耗時: %d ms",
                completedTasks.get(), (System.currentTimeMillis() - startTime));
    }

    @GetMapping("/db-safe-100k")
    public String dbSafe100k() {
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 100_000; i++) {
                executor.submit(() -> {
                    try {
                        // 1. 在 Java 層級排隊，不要直接去撞 HikariCP
                        dbLimit.acquire();

                        // 2. 拿到許可證後，再去拿 DB 連線
                        userRepository.findById(1L);
                        successCount.incrementAndGet();

                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        // 3. 務必釋放許可證，讓後面的人進來
                        dbLimit.release();
                    }
                    return null;
                });
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        return String.format("實驗結果：成功 %d, 失敗 %d, 總耗時: %d ms",
                successCount.get(), failCount.get(), duration);
    }

    // 實驗：每個請求就是一個獨立的任務，由 Spring MVC + Virtual Threads 自動調度
    @GetMapping("/io-bound")
    public String ioBound() throws InterruptedException {
        // 模擬 1 秒的 IO 阻塞 (例如呼叫外部 API 或查資料庫)
        Thread.sleep(1000);
        return "OK";
    }
}