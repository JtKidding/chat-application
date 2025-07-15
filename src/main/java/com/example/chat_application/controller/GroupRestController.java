package com.example.chat_application.controller;

import com.example.chat_application.entity.Group;
import com.example.chat_application.entity.User;
import com.example.chat_application.entity.Message;
import com.example.chat_application.service.GroupService;
import com.example.chat_application.service.UserService;
import com.example.chat_application.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

@RestController
@RequestMapping("/api/groups")
public class GroupRestController {

    @Autowired
    private GroupService groupService;

    @Autowired
    private UserService userService;

    @Autowired
    private MessageService messageService;

    // 獲取用戶的群組列表
    @GetMapping("/my")
    public ResponseEntity<List<Map<String, Object>>> getMyGroups(Authentication authentication) {
        try {
            String username = authentication.getName();
            User currentUser = userService.findByUsername(username).orElse(null);

            if (currentUser == null) {
                return ResponseEntity.notFound().build();
            }

            List<Group> groups = groupService.findByUser(currentUser);

            List<Map<String, Object>> groupList = groups.stream()
                    .map(group -> {
                        Map<String, Object> groupInfo = new HashMap<>();
                        groupInfo.put("id", group.getId());
                        groupInfo.put("name", group.getName());
                        groupInfo.put("description", group.getDescription());
                        groupInfo.put("avatarUrl", group.getAvatarUrl() != null ?
                                "/uploads/groups/" + group.getAvatarUrl().substring(group.getAvatarUrl().lastIndexOf("/") + 1) :
                                "/images/default-group.png");
                        groupInfo.put("memberCount", group.getMembers().size());
                        groupInfo.put("isCreator", group.isCreator(currentUser));
                        groupInfo.put("createdAt", group.getCreatedAt().toString());

                        // 獲取最後一條訊息
                        Optional<Message> lastMessage = messageService.getLatestGroupMessage(group);
                        if (lastMessage.isPresent()) {
                            Message msg = lastMessage.get();
                            Map<String, Object> lastMsgInfo = new HashMap<>();
                            lastMsgInfo.put("content", msg.getContent());
                            lastMsgInfo.put("timestamp", msg.getTimestamp().toString());
                            lastMsgInfo.put("senderName", msg.getSender() != null ?
                                    msg.getSender().getDisplayName() : "系統");
                            lastMsgInfo.put("messageType", msg.getMessageType().toString());
                            groupInfo.put("lastMessage", lastMsgInfo);
                        }

                        return groupInfo;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(groupList);

        } catch (Exception e) {
            System.err.println("獲取用戶群組失敗: " + e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }

    // 獲取群組詳細資訊
    @GetMapping("/{groupId}")
    public ResponseEntity<Map<String, Object>> getGroupDetails(
            @PathVariable Long groupId,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            User currentUser = userService.findByUsername(username).orElse(null);

            if (currentUser == null) {
                return ResponseEntity.notFound().build();
            }

            Optional<Group> groupOpt = groupService.findById(groupId);
            if (groupOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Group group = groupOpt.get();

            // 檢查用戶是否為群組成員
            if (!group.isMember(currentUser)) {
                return ResponseEntity.status(403).build();
            }

            Map<String, Object> groupInfo = new HashMap<>();
            groupInfo.put("id", group.getId());
            groupInfo.put("name", group.getName());
            groupInfo.put("description", group.getDescription());
            groupInfo.put("avatarUrl", group.getAvatarUrl() != null ?
                    "/uploads/groups/" + group.getAvatarUrl().substring(group.getAvatarUrl().lastIndexOf("/") + 1) :
                    "/images/default-group.png");
            groupInfo.put("isPrivate", group.isPrivate());
            groupInfo.put("maxMembers", group.getMaxMembers());
            groupInfo.put("createdAt", group.getCreatedAt().toString());
            groupInfo.put("isCreator", group.isCreator(currentUser));

            // 群組成員資訊
            List<Map<String, Object>> memberList = group.getMembers().stream()
                    .map(member -> {
                        Map<String, Object> memberInfo = new HashMap<>();
                        memberInfo.put("id", member.getId());
                        memberInfo.put("username", member.getUsername());
                        memberInfo.put("displayName", member.getDisplayName());
                        memberInfo.put("avatarUrl", member.getAvatarUrl() != null ?
                                "/uploads/avatars/" + member.getAvatarUrl().substring(member.getAvatarUrl().lastIndexOf("/") + 1) :
                                "/images/default-avatar.png");
                        memberInfo.put("isOnline", member.isOnline());
                        memberInfo.put("isCreator", group.isCreator(member));
                        return memberInfo;
                    })
                    .collect(Collectors.toList());

            groupInfo.put("members", memberList);

            // 群組統計
            GroupService.GroupStats stats = groupService.getGroupStats(group);
            Map<String, Object> statsInfo = new HashMap<>();
            statsInfo.put("memberCount", stats.getMemberCount());
            statsInfo.put("messageCount", stats.getMessageCount());
            groupInfo.put("stats", statsInfo);

            return ResponseEntity.ok(groupInfo);

        } catch (Exception e) {
            System.err.println("獲取群組詳情失敗: " + e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }

    // 獲取群組聊天記錄
    @GetMapping("/{groupId}/messages")
    public ResponseEntity<List<Map<String, Object>>> getGroupMessages(
            @PathVariable Long groupId,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            User currentUser = userService.findByUsername(username).orElse(null);

            if (currentUser == null) {
                return ResponseEntity.notFound().build();
            }

            Optional<Group> groupOpt = groupService.findById(groupId);
            if (groupOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Group group = groupOpt.get();

            // 檢查用戶是否為群組成員
            if (!group.isMember(currentUser)) {
                return ResponseEntity.status(403).build();
            }

            List<Message> messages = messageService.getGroupConversation(group);


            List<Map<String, Object>> messageList = messages.stream()
                    .map(message -> {
                        Map<String, Object> messageMap = new HashMap<>();
                        messageMap.put("id", message.getId());
                        messageMap.put("content", message.getContent());
                        messageMap.put("timestamp", message.getTimestamp().toString());
                        messageMap.put("messageType", message.getMessageType().toString());

                        if (message.getSender() != null) {
                            messageMap.put("senderUsername", message.getSender().getUsername());
                            messageMap.put("senderDisplayName", message.getSender().getDisplayName());
                            messageMap.put("senderAvatarUrl", message.getSender().getAvatarUrl() != null ?
                                    "/uploads/avatars/" + message.getSender().getAvatarUrl().substring(message.getSender().getAvatarUrl().lastIndexOf("/") + 1) :
                                    "/images/default-avatar.png");
                        } else {
                            messageMap.put("senderUsername", "system");
                            messageMap.put("senderDisplayName", "系統");
                            messageMap.put("senderAvatarUrl", "/images/system-avatar.png");
                        }

                        return messageMap;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(messageList);

        } catch (Exception e) {
            System.err.println("獲取群組訊息失敗: " + e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }

    // 搜尋公開群組
    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> searchGroups(
            @RequestParam String query,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            User currentUser = userService.findByUsername(username).orElse(null);

            if (currentUser == null) {
                return ResponseEntity.notFound().build();
            }

            List<Group> groups = groupService.searchPublicGroups(query);

            List<Map<String, Object>> groupList = groups.stream()
                    .map(group -> {
                        Map<String, Object> groupInfo = new HashMap<>();
                        groupInfo.put("id", group.getId());
                        groupInfo.put("name", group.getName());
                        groupInfo.put("description", group.getDescription());
                        groupInfo.put("avatarUrl", group.getAvatarUrl() != null ?
                                "/uploads/groups/" + group.getAvatarUrl().substring(group.getAvatarUrl().lastIndexOf("/") + 1) :
                                "/images/default-group.png");
                        groupInfo.put("memberCount", group.getMembers().size());
                        groupInfo.put("isMember", group.isMember(currentUser));
                        groupInfo.put("createdAt", group.getCreatedAt().toString());
                        return groupInfo;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(groupList);

        } catch (Exception e) {
            System.err.println("搜尋群組失敗: " + e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }

    // 移除群組成員
    @PostMapping("/{groupId}/members/{userId}/remove")
    public ResponseEntity<Map<String, Object>> removeMember(
            @PathVariable Long groupId,
            @PathVariable Long userId,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            User currentUser = userService.findByUsername(username).orElse(null);
            User memberToRemove = userService.findById(userId).orElse(null);

            if (currentUser == null) {
                return ResponseEntity.status(401).body(Map.of("error", "用戶未認證"));
            }

            if (memberToRemove == null) {
                return ResponseEntity.status(404).body(Map.of("error", "要移除的用戶不存在"));
            }

            // 調用 GroupService 的 removeMember 方法
            boolean success = groupService.removeMember(groupId, currentUser, memberToRemove);

            if (success) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", memberToRemove.getDisplayName() + " 已被移出群組",
                        "removedUserId", userId,
                        "removedUserName", memberToRemove.getDisplayName()
                ));
            } else {
                return ResponseEntity.status(500).body(Map.of("error", "移除成員失敗"));
            }

        } catch (RuntimeException e) {
            System.err.println("移除成員失敗: " + e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            System.err.println("移除成員時發生未預期錯誤: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "伺服器內部錯誤: " + e.getMessage()));
        }
    }

    // 獲取群組成員列表
    @GetMapping("/{groupId}/members")
    public ResponseEntity<List<Map<String, Object>>> getGroupMembers(
            @PathVariable Long groupId,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            User currentUser = userService.findByUsername(username).orElse(null);

            if (currentUser == null) {
                return ResponseEntity.notFound().build();
            }

            Optional<Group> groupOpt = groupService.findById(groupId);
            if (groupOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Group group = groupOpt.get();

            // 檢查用戶是否為群組成員
            if (!group.isMember(currentUser)) {
                return ResponseEntity.status(403).build();
            }

            List<Map<String, Object>> memberList = group.getMembers().stream()
                    .map(member -> {
                        Map<String, Object> memberInfo = new HashMap<>();
                        memberInfo.put("id", member.getId());
                        memberInfo.put("username", member.getUsername());
                        memberInfo.put("displayName", member.getDisplayName());
                        memberInfo.put("avatarUrl", member.getAvatarUrl() != null ?
                                "/uploads/avatars/" + member.getAvatarUrl().substring(member.getAvatarUrl().lastIndexOf("/") + 1) :
                                "/images/default-avatar.png");
                        memberInfo.put("isOnline", member.isOnline());
                        memberInfo.put("isCreator", group.isCreator(member));
                        memberInfo.put("joinedAt", member.getCreatedAt().toString());
                        return memberInfo;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(memberList);

        } catch (Exception e) {
            System.err.println("獲取群組成員失敗: " + e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }

    // 檢查用戶是否為群組成員
    @GetMapping("/{groupId}/membership")
    public ResponseEntity<Map<String, Object>> checkMembership(
            @PathVariable Long groupId,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            User currentUser = userService.findByUsername(username).orElse(null);

            if (currentUser == null) {
                return ResponseEntity.notFound().build();
            }

            boolean isMember = groupService.isUserMemberOfGroup(groupId, currentUser);

            Map<String, Object> result = new HashMap<>();
            result.put("isMember", isMember);
            result.put("userId", currentUser.getId());
            result.put("groupId", groupId);

            if (isMember) {
                Optional<Group> groupOpt = groupService.findById(groupId);
                if (groupOpt.isPresent()) {
                    Group group = groupOpt.get();
                    result.put("isCreator", group.isCreator(currentUser));
                    result.put("groupName", group.getName());
                }
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            System.err.println("檢查群組成員資格失敗: " + e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }

    // 獲取群組統計資訊
    @GetMapping("/{groupId}/stats")
    public ResponseEntity<Map<String, Object>> getGroupStats(
            @PathVariable Long groupId,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            User currentUser = userService.findByUsername(username).orElse(null);

            if (currentUser == null) {
                return ResponseEntity.notFound().build();
            }

            Optional<Group> groupOpt = groupService.findById(groupId);
            if (groupOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Group group = groupOpt.get();

            // 檢查用戶是否為群組成員
            if (!group.isMember(currentUser)) {
                return ResponseEntity.status(403).build();
            }

            GroupService.GroupStats stats = groupService.getGroupStats(group);

            Map<String, Object> statsMap = new HashMap<>();
            statsMap.put("memberCount", stats.getMemberCount());
            statsMap.put("messageCount", stats.getMessageCount());
            statsMap.put("createdAt", stats.getCreatedAt().toString());
            statsMap.put("groupId", groupId);
            statsMap.put("groupName", group.getName());

            return ResponseEntity.ok(statsMap);

        } catch (Exception e) {
            System.err.println("獲取群組統計失敗: " + e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }

    @PostMapping("/{groupId}/dissolve")
    public ResponseEntity<Map<String, Object>> dissolveGroup(
            @PathVariable Long groupId,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            User currentUser = userService.findByUsername(username).orElse(null);

            if (currentUser == null) {
                return ResponseEntity.status(401).body(Map.of("error", "用戶未認證"));
            }

            // 獲取群組資訊（用於返回名稱）
            Optional<Group> groupOpt = groupService.findById(groupId);
            if (groupOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "群組不存在"));
            }

            Group group = groupOpt.get();
            String groupName = group.getName();

            // 調用 GroupService 的 dissolveGroup 方法
            boolean success = groupService.dissolveGroup(groupId, currentUser);

            if (success) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "群組「" + groupName + "」已成功解散",
                        "groupId", groupId,
                        "groupName", groupName
                ));
            } else {
                return ResponseEntity.status(500).body(Map.of("error", "解散群組失敗"));
            }

        } catch (RuntimeException e) {
            System.err.println("解散群組失敗: " + e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            System.err.println("解散群組時發生未預期錯誤: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "伺服器內部錯誤: " + e.getMessage()));
        }
    }
}