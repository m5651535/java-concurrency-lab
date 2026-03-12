package com.lab.nio.reactive.mvc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan("com.lab.nio.reactive.mvc.entity") // 指定掃描 JPA Entity
@EnableJpaRepositories("com.lab.nio.reactive.mvc.repository") // 指定掃描 JPA Repository
public class MvcApplication {
	public static void main(String[] args) {
		SpringApplication.run(MvcApplication.class, args);
	}
}