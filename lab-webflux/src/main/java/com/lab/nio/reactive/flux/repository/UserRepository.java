package com.lab.nio.reactive.flux.repository;

import com.lab.nio.reactive.flux.entity.User;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends ReactiveCrudRepository<User, Long> {
    // 這裡的方法是非阻塞的，回傳 Mono<User> 或 Flux<User>
}
