package com.example.chat_application.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String displayName;

    @Column
    private String email;

    @Column
    private String phoneNumber;

    @Column
    private String bio; // 個人簡介

    @Column
    private String avatarUrl; // 頭像URL或檔案路徑

    @Column
    private LocalDateTime lastSeen;

    @Column
    private boolean online = false;

    @Column
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    // 用戶創建的群組
    @OneToMany(mappedBy = "creator", fetch = FetchType.LAZY)
    private Set<Group> createdGroups = new HashSet<>();

    // 用戶參與的群組
    @ManyToMany(mappedBy = "members", fetch = FetchType.LAZY)
    private Set<Group> joinedGroups = new HashSet<>();

    // 建構子
    public User() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public User(String username, String password, String displayName) {
        this();
        this.username = username;
        this.password = password;
        this.displayName = displayName;
        this.lastSeen = LocalDateTime.now();
    }

    // 更新時間
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getter 和 Setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public LocalDateTime getLastSeen() { return lastSeen; }
    public void setLastSeen(LocalDateTime lastSeen) { this.lastSeen = lastSeen; }

    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Set<Group> getCreatedGroups() { return createdGroups; }
    public void setCreatedGroups(Set<Group> createdGroups) { this.createdGroups = createdGroups; }

    public Set<Group> getJoinedGroups() { return joinedGroups; }
    public void setJoinedGroups(Set<Group> joinedGroups) { this.joinedGroups = joinedGroups; }
}