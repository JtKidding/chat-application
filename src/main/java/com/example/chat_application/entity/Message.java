package com.example.chat_application.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sender_id")
    private User sender;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "recipient_id")
    private User recipient;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "group_id")
    private Group group;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column
    private boolean isRead = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageType messageType = MessageType.TEXT;

    // 訊息類型枚舉
    public enum MessageType {
        TEXT,           // 文字訊息
        IMAGE,          // 圖片
        FILE,           // 檔案
        SYSTEM          // 系統訊息（如用戶加入/離開群組）
    }

    // 建構子
    public Message() {}

    // 私人聊天建構子
    public Message(User sender, User recipient, String content) {
        this.sender = sender;
        this.recipient = recipient;
        this.content = content;
        this.timestamp = LocalDateTime.now();
    }

    // 群組聊天建構子
    public Message(User sender, Group group, String content) {
        this.sender = sender;
        this.group = group;
        this.content = content;
        this.timestamp = LocalDateTime.now();
    }

    // 系統訊息建構子
    public Message(Group group, String content, MessageType messageType) {
        this.group = group;
        this.content = content;
        this.messageType = messageType;
        this.timestamp = LocalDateTime.now();
        this.isRead = true; // 系統訊息預設已讀
        this.sender = null; // 系統訊息沒有發送者
    }

    // 輔助方法
    public boolean isPrivateMessage() {
        return this.recipient != null && this.group == null;
    }

    public boolean isGroupMessage() {
        return this.group != null && this.recipient == null;
    }

    public boolean isSystemMessage() {
        return this.messageType == MessageType.SYSTEM;
    }

    // Getter 和 Setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getSender() { return sender; }
    public void setSender(User sender) { this.sender = sender; }

    public User getRecipient() { return recipient; }
    public void setRecipient(User recipient) { this.recipient = recipient; }

    public Group getGroup() { return group; }
    public void setGroup(Group group) { this.group = group; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

    public MessageType getMessageType() { return messageType; }
    public void setMessageType(MessageType messageType) { this.messageType = messageType; }
}
