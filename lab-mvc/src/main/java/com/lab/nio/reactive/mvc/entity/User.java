package com.lab.nio.reactive.mvc.entity;

import jakarta.persistence.*; // [重點] 確保是 jakarta.persistence
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "users")
public class User {

    @Id // [核心] 必須加上這個註解
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 自動跳號
    private Long id;

    private String username;
    private String email;

    // JPA 規範：必須有一個無參構造函數 (Protected 或 Public)
    public User() {}

    public User(String username, String email) {
        this.username = username;
        this.email = email;
    }

}