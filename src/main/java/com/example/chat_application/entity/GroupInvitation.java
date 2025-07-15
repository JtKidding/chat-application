package com.example.chat_application.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "group_invitations")
public class GroupInvitation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "inviter_id", nullable = false)
    private User inviter; // 邀請者

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "invitee_id", nullable = false)
    private User invitee; // 被邀請者

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvitationStatus status = InvitationStatus.PENDING;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime respondedAt; // 回應時間

    @Column(length = 500)
    private String message; // 邀請訊息

    @Column
    private LocalDateTime expiresAt; // 邀請過期時間

    // 邀請狀態枚舉
    public enum InvitationStatus {
        PENDING,    // 待處理
        ACCEPTED,   // 已接受
        DECLINED,   // 已拒絕
        EXPIRED,    // 已過期
        CANCELLED   // 已取消
    }

    // 建構子
    public GroupInvitation() {
        this.createdAt = LocalDateTime.now();
        // 設置邀請7天後過期
        this.expiresAt = LocalDateTime.now().plusDays(7);
    }

    public GroupInvitation(Group group, User inviter, User invitee, String message) {
        this();
        this.group = group;
        this.inviter = inviter;
        this.invitee = invitee;
        this.message = message;
    }

    // 輔助方法
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }

    public boolean isPending() {
        return this.status == InvitationStatus.PENDING && !isExpired();
    }

    public void accept() {
        this.status = InvitationStatus.ACCEPTED;
        this.respondedAt = LocalDateTime.now();
    }

    public void decline() {
        this.status = InvitationStatus.DECLINED;
        this.respondedAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = InvitationStatus.CANCELLED;
        this.respondedAt = LocalDateTime.now();
    }

    public void markExpired() {
        this.status = InvitationStatus.EXPIRED;
        this.respondedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Group getGroup() { return group; }
    public void setGroup(Group group) { this.group = group; }

    public User getInviter() { return inviter; }
    public void setInviter(User inviter) { this.inviter = inviter; }

    public User getInvitee() { return invitee; }
    public void setInvitee(User invitee) { this.invitee = invitee; }

    public InvitationStatus getStatus() { return status; }
    public void setStatus(InvitationStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getRespondedAt() { return respondedAt; }
    public void setRespondedAt(LocalDateTime respondedAt) { this.respondedAt = respondedAt; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}