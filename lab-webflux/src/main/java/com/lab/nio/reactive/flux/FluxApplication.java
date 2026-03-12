package com.lab.nio.reactive.flux;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

@SpringBootApplication
@EnableR2dbcRepositories("com.lab.nio.reactive.flux.repository") // 指定掃描 R2DBC Repository
public class FluxApplication {
	public static void main(String[] args) {
		SpringApplication.run(FluxApplication.class, args);
	}
}