package com.example.chat_application.controller;

import com.example.chat_application.entity.Group;
import com.example.chat_application.entity.Message;
import com.example.chat_application.entity.User;
import com.example.chat_application.repository.GroupRepository;
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

    @Autowired
    private GroupRepository groupRepository;

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

        System.out.println("=== 收到群組訊息請求 ===");
        System.out.println("發送者: " + senderUsername);
        System.out.println("群組ID: " + groupIdStr);
        System.out.println("內容: " + content);

        try {
            Long groupId = Long.parseLong(groupIdStr);
            User sender = userService.findByUsername(senderUsername).orElse(null);

            if (sender == null) {
                System.err.println("找不到發送者用戶: " + senderUsername);
                return;
            }

//            // 使用安全的成員檢查方法
//            boolean isMember = groupService.isUserMemberOfGroup(groupId, sender);
//            System.out.println("用戶是否為群組成員: " + isMember);

            System.out.println("發送者用戶 ID: " + sender.getId() + ", 用戶名: " + sender.getUsername());

            // 使用多種方法檢查成員資格，確保準確性
            boolean isMemberByService = groupService.isUserMemberOfGroup(groupId, sender);
            boolean isMemberByRepository = groupRepository.isUserMemberOfGroupByUsername(groupId, senderUsername);

            System.out.println("Service 檢查結果: " + isMemberByService);
            System.out.println("Repository 檢查結果: " + isMemberByRepository);

            // 如果任一方法返回 true，就認為是成員
            boolean isMember = isMemberByService || isMemberByRepository;

            System.out.println("最終成員檢查結果: " + isMember);

            if (!isMember) {
                System.err.println("用戶不是群組成員，拒絕發送訊息");

                // 額外調試：查看群組的所有成員
                try {
                    Optional<Group> debugGroup = groupService.findByIdWithMembers(groupId);
                    if (debugGroup.isPresent()) {
                        System.err.println("群組 " + debugGroup.get().getName() + " 的所有成員:");
                        debugGroup.get().getMembers().forEach(member ->
                                System.err.println("  - ID: " + member.getId() + ", 用戶名: " + member.getUsername())
                        );
                    }
                } catch (Exception debugEx) {
                    System.err.println("調試群組成員時發生錯誤: " + debugEx.getMessage());
                }

                return;
            }

            // 獲取群組信息（帶有已初始化的成員）
            Optional<Group> groupOpt = groupService.findByIdWithMembers(groupId);

            if (groupOpt.isPresent()) {
                Group group = groupOpt.get();
                System.out.println("群組資訊: " + group.getName() + ", 成員數: " + group.getMembers().size());

                // 儲存群組訊息到資料庫
                Message message = messageService.saveGroupMessage(sender, group, content);
                System.out.println("訊息已儲存到資料庫，ID: " + message.getId());

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

                System.out.println("準備發送訊息給 " + group.getMembers().size() + " 位成員");

                // 發送訊息給群組中的所有成員
                int sentCount = 0;
                for (User member : group.getMembers()) {
                    try {
                        messagingTemplate.convertAndSendToUser(
                                member.getUsername(),
                                "/queue/group-messages",
                                messageData
                        );
                        sentCount++;
                        System.out.println("已發送給: " + member.getUsername());
                    } catch (Exception e) {
                        System.err.println("發送給用戶 " + member.getUsername() + " 失敗: " + e.getMessage());
                    }
                }

                System.out.println("總共發送給 " + sentCount + " 位成員");
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

    @MessageMapping("/chat.heartbeat")
    public void handleHeartbeat(@Payload Map<String, String> payload, Principal principal) {
        String username = principal.getName();
        String timestamp = payload.get("timestamp");

        try {
            // 更新用戶的最後活動時間
            userService.updateLastSeen(username);

            // 確保用戶狀態為線上
            userService.setUserOnline(username, true);

            System.out.println("收到用戶 " + username + " 的心跳，時間: " + timestamp);
        } catch (Exception e) {
            System.err.println("處理心跳時發生錯誤: " + e.getMessage());
        }
    }
}
