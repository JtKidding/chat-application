package com.example.chat_application.controller;

import com.example.chat_application.entity.Message;
import com.example.chat_application.entity.User;
import com.example.chat_application.repository.MessageRepository;
import com.example.chat_application.service.MessageService;
import com.example.chat_application.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api")
public class ChatRestController {

    @Autowired
    private MessageService messageService;

    @Autowired
    private UserService userService;

    // 取得聊天歷史記錄
    @GetMapping("/messages/{username}")
    public ResponseEntity<List<Map<String, Object>>> getChatHistory(
            @PathVariable String username,
            Authentication authentication) {

        String currentUsername = authentication.getName();
        User currentUser = userService.findByUsername(currentUsername).orElse(null);
        User otherUser = userService.findByUsername(username).orElse(null);

        if (currentUser == null || otherUser == null) {
            return ResponseEntity.notFound().build();
        }

        List<Message> messages = messageService.getConversation(currentUser, otherUser);

        List<Map<String, Object>> messageList = messages.stream()
                .sorted((m1, m2) -> m1.getTimestamp().compareTo(m2.getTimestamp()))
                .map(message -> {
                    Map<String, Object> messageMap = new HashMap<>();
                    messageMap.put("id", message.getId());
                    messageMap.put("senderUsername", message.getSender().getUsername());
                    messageMap.put("senderDisplayName", message.getSender().getDisplayName());
                    messageMap.put("senderAvatarUrl", message.getSender().getAvatarUrl() != null ?
                            "/uploads/avatars/" + message.getSender().getAvatarUrl().substring(message.getSender().getAvatarUrl().lastIndexOf("/") + 1) :
                            "/images/default-avatar.png");
                    messageMap.put("content", message.getContent());
                    messageMap.put("timestamp", message.getTimestamp().toString());
                    messageMap.put("isRead", message.isRead());
                    return messageMap;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(messageList);
    }

    // 獲取當前用戶與所有聊天夥伴的最後一條訊息
    @GetMapping("/messages/latest")
    public ResponseEntity<Map<String, Object>> getLatestMessages(Authentication authentication) {
        try {
            String currentUsername = authentication.getName();
            User currentUser = userService.findByUsername(currentUsername).orElse(null);

            if (currentUser == null) {
                return ResponseEntity.notFound().build();
            }

            Map<String, Object> latestMessages = new HashMap<>();

            // 獲取所有聊天夥伴
            List<User> chatPartners = messageService.getChatPartners(currentUser);

            if (chatPartners == null || chatPartners.isEmpty()) {
                // 沒有聊天夥伴，返回空的Map
                return ResponseEntity.ok(latestMessages);
            }

            for (User partner : chatPartners) {
                try {
                    // 獲取與每個夥伴的最後一條訊息
                    List<Message> conversation = messageService.getConversation(currentUser, partner);

                    if (conversation != null && !conversation.isEmpty()) {
                        Message lastMessage = conversation.get(conversation.size() - 1);

                        Map<String, Object> messageInfo = new HashMap<>();
                        messageInfo.put("content", lastMessage.getContent());
                        messageInfo.put("timestamp", lastMessage.getTimestamp().toString());
                        messageInfo.put("senderUsername", lastMessage.getSender().getUsername());
                        messageInfo.put("isRead", lastMessage.isRead());

                        latestMessages.put(partner.getUsername(), messageInfo);
                    }
                } catch (Exception e) {
                    System.err.println("處理用戶 " + partner.getUsername() + " 的對話時發生錯誤: " + e.getMessage());
                    // 繼續處理下一個用戶，不讓一個錯誤影響整個API
                    continue;
                }
            }

            return ResponseEntity.ok(latestMessages);

        } catch (Exception e) {
            System.err.println("獲取最新訊息時發生錯誤: " + e.getMessage());
            e.printStackTrace();

            // 返回空的結果而不是錯誤，讓前端能正常運作
            Map<String, Object> emptyResult = new HashMap<>();
            return ResponseEntity.ok(emptyResult);
        }
    }

    // 取得線上用戶列表
    @GetMapping("/users/online")
    public ResponseEntity<List<Map<String, Object>>> getOnlineUsers(Authentication authentication) {
        String currentUsername = authentication.getName();
        List<User> onlineUsers = userService.findOnlineUsers()
                .stream()
                .filter(user -> !user.getUsername().equals(currentUsername))
                .collect(Collectors.toList());

        List<Map<String, Object>> userList = onlineUsers.stream()
                .map(user -> {
                    Map<String, Object> userMap = new HashMap<>();
                    userMap.put("username", user.getUsername());
                    userMap.put("displayName", user.getDisplayName());
                    userMap.put("online", user.isOnline());
                    userMap.put("avatarUrl", user.getAvatarUrl() != null ?
                            "/uploads/avatars/" + user.getAvatarUrl().substring(user.getAvatarUrl().lastIndexOf("/") + 1) :
                            "/images/default-avatar.png");
                    userMap.put("bio", user.getBio());
                    return userMap;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(userList);
    }

    // 標記與特定用戶的對話為已讀
    @PostMapping("/messages/{username}/mark-read")
    public ResponseEntity<Void> markConversationAsRead(
            @PathVariable String username,
            Authentication authentication) {
        try {
            String currentUsername = authentication.getName();
            User currentUser = userService.findByUsername(currentUsername).orElse(null);
            User otherUser = userService.findByUsername(username).orElse(null);

            if (currentUser != null && otherUser != null) {
                messageService.markConversationAsRead(currentUser, otherUser);
            }

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            System.err.println("標記訊息已讀失敗: " + e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }
}