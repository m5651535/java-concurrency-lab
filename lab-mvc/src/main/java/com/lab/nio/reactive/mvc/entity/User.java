package com.lab.nio.reactive.mvc.entity;

import jakarta.persistence.*; // [重點] 確保是 jakarta.persistence

@Entity
@Table(name = "users")
public class User {

    @Id // [核心] 必須加上這個註解
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 自動跳號
    private Long id;

    private String name;
    private String email;

    // JPA 規範：必須有一個無參構造函數 (Protected 或 Public)
    protected User() {}

    public User(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}