package com.example.chat_application.controller;

import com.example.chat_application.entity.Group;
import com.example.chat_application.entity.Message;
import com.example.chat_application.entity.User;
import com.example.chat_application.service.GroupService;
import com.example.chat_application.service.MessageService;
import com.example.chat_application.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

@Controller
public class ChatController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private MessageService messageService;

    @Autowired
    private UserService userService;

    @Autowired
    private GroupService groupService;

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload Map<String, String> payload, Principal principal) {
        String senderUsername = principal.getName();
        String recipientUsername = payload.get("recipientUsername");
        String content = payload.get("content");

        User sender = userService.findByUsername(senderUsername).orElse(null);
        User recipient = userService.findByUsername(recipientUsername).orElse(null);

        if (sender != null && recipient != null) {
            Message message = messageService.saveMessage(sender, recipient, content);

            // 準備訊息資料
            Map<String, Object> messageData = Map.of(
                    "id", message.getId(),
                    "senderUsername", sender.getUsername(),
                    "senderDisplayName", sender.getDisplayName(),
                    "senderAvatarUrl", sender.getAvatarUrl() != null ?
                            "/uploads/avatars/" + sender.getAvatarUrl().substring(sender.getAvatarUrl().lastIndexOf("/") + 1) :
                            "/images/default-avatar.png",
                    "content", message.getContent(),
                    "timestamp", message.getTimestamp().toString(),
                    "recipientUsername", recipient.getUsername(),
                    "messageType", "private"
            );

            // 發送訊息給接收者
            messagingTemplate.convertAndSendToUser(
                    recipientUsername,
                    "/queue/messages",
                    messageData
            );

            // 發送確認給發送者
            messagingTemplate.convertAndSendToUser(
                    senderUsername,
                    "/queue/messages",
                    messageData
            );

            /*// 發送訊息給接收者
            messagingTemplate.convertAndSendToUser(
                    recipientUsername,
                    "/queue/messages",
                    Map.of(
                            "id", message.getId(),
                            "senderUsername", sender.getUsername(),
                            "senderDisplayName", sender.getDisplayName(),
                            "content", message.getContent(),
                            "timestamp", message.getTimestamp().toString()
                    )
            );

            // 發送確認給發送者
            messagingTemplate.convertAndSendToUser(
                    senderUsername,
                    "/queue/messages",
                    Map.of(
                            "id", message.getId(),
                            "senderUsername", sender.getUsername(),
                            "senderDisplayName", sender.getDisplayName(),
                            "content", message.getContent(),
                            "timestamp", message.getTimestamp().toString(),
                            "recipientUsername", recipient.getUsername()
                    )
            );*/
        }
    }

    // 處理群組訊息
    @MessageMapping("/chat.sendGroupMessage")
    public void sendGroupMessage(@Payload Map<String, String> payload, Principal principal) {
        String senderUsername = principal.getName();
        String groupIdStr = payload.get("groupId");
        String content = payload.get("content");

        try {
            Long groupId = Long.parseLong(groupIdStr);
            User sender = userService.findByUsername(senderUsername).orElse(null);

            if (sender != null) {
                // 使用安全的成員檢查方法
                boolean isMember = groupService.isUserMemberOfGroup(groupId, sender);

                if (!isMember) {
                    return; // 非群組成員，拒絕發送
                }

                // 獲取群組信息（帶有已初始化的成員）
                Optional<Group> groupOpt = groupService.findByIdWithMembers(groupId);

                if (groupOpt.isPresent()) {
                    Group group = groupOpt.get();

                    // 儲存群組訊息到資料庫
                    Message message = messageService.saveGroupMessage(sender, group, content);

                    // 準備訊息資料
                    Map<String, Object> messageData = Map.of(
                            "id", message.getId(),
                            "senderUsername", sender.getUsername(),
                            "senderDisplayName", sender.getDisplayName(),
                            "senderAvatarUrl", sender.getAvatarUrl() != null ?
                                    "/uploads/avatars/" + sender.getAvatarUrl().substring(sender.getAvatarUrl().lastIndexOf("/") + 1) :
                                    "/images/default-avatar.png",
                            "content", message.getContent(),
                            "timestamp", message.getTimestamp().toString(),
                            "groupId", group.getId(),
                            "groupName", group.getName(),
                            "messageType", "group"
                    );

                    // 發送訊息給群組中的所有成員
                    group.getMembers().forEach(member -> {
                        messagingTemplate.convertAndSendToUser(
                                member.getUsername(),
                                "/queue/group-messages",
                                messageData
                        );
                    });
                }
            }
        } catch (NumberFormatException e) {
            System.err.println("無效的群組ID: " + groupIdStr);
        } catch (Exception e) {
            System.err.println("發送群組訊息時發生錯誤: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 處理用戶上線
    @MessageMapping("/chat.addUser")
    public void addUser(@Payload Map<String, String> payload, Principal principal) {
        String username = principal.getName();
        userService.setUserOnline(username, true);

        // 獲取用戶資訊
        User user = userService.findByUsername(username).orElse(null);
        if (user != null) {
            // 廣播用戶上線狀態
            messagingTemplate.convertAndSend("/topic/public",
                    Map.of(
                            "type", "JOIN",
                            "sender", username,
                            "displayName", user.getDisplayName(),
                            "avatarUrl", user.getAvatarUrl() != null ?
                                    "/uploads/avatars/" + user.getAvatarUrl().substring(user.getAvatarUrl().lastIndexOf("/") + 1) :
                                    "/images/default-avatar.png"
                    )
            );
        }
    }

    // 處理用戶下線
    @MessageMapping("/chat.removeUser")
    public void removeUser(@Payload Map<String, String> payload, Principal principal) {
        String username = principal.getName();
        userService.setUserOnline(username, false);

        // 廣播用戶下線狀態
        messagingTemplate.convertAndSend("/topic/public",
                Map.of("type", "LEAVE", "sender", username));
    }

    // 處理加入群組
    @MessageMapping("/chat.joinGroup")
    @Transactional
    public void joinGroup(@Payload Map<String, String> payload, Principal principal) {
        String username = principal.getName();
        String groupIdStr = payload.get("groupId");

        try {
            Long groupId = Long.parseLong(groupIdStr);
            User user = userService.findByUsername(username).orElse(null);
//            Optional<Group> groupOpt = groupService.findById(groupId);

            if (user != null) {
                // 使用 GroupService 的安全方法檢查成員資格
                boolean isMember = groupService.isUserMemberOfGroup(groupId, user);

                if (isMember) {
                    // 獲取群組信息（帶有已初始化的成員）
                    Optional<Group> groupOpt = groupService.findByIdWithMembers(groupId);

                    if (groupOpt.isPresent()) {
                        Group group = groupOpt.get();

                        // 檢查用戶是否為群組成員
                        if (group.isMember(user)) {
                            // 通知群組成員用戶已上線
                            Map<String, Object> joinData = Map.of(
                                    "type", "USER_ONLINE",
                                    "username", username,
                                    "displayName", user.getDisplayName(),
                                    "groupId", groupId
                            );

                            group.getMembers().forEach(member -> {
                                messagingTemplate.convertAndSendToUser(
                                        member.getUsername(),
                                        "/queue/group-updates",
                                        joinData
                                );
                            });
                        }
                    }
                }
            }
        } catch (NumberFormatException e) {
            System.err.println("無效的群組ID: " + groupIdStr);
        } catch (Exception e) {
            System.err.println("加入群組時發生錯誤: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 處理離開群組
    @MessageMapping("/chat.leaveGroup")
    public void leaveGroup(@Payload Map<String, String> payload, Principal principal) {
        String username = principal.getName();
        String groupIdStr = payload.get("groupId");

        try {
            Long groupId = Long.parseLong(groupIdStr);

            // 通知群組成員用戶已下線
            Map<String, Object> leaveData = Map.of(
                    "type", "USER_OFFLINE",
                    "username", username,
                    "groupId", groupId
            );

            messagingTemplate.convertAndSend("/topic/group-" + groupId, leaveData);

        } catch (NumberFormatException e) {
            System.err.println("無效的群組ID: " + groupIdStr);
        }
    }
}
