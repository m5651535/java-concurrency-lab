package com.lab.nio.reactive.flux.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("users") // 注意：R2DBC 不使用 @Entity
public class User {
    @Id
    private Long id;

    private String username;
    private String email;

    public User(Long id, String username, String email) {
        this.id = id;
        this.username = username;
        this.email = email;
    }

    public User() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
