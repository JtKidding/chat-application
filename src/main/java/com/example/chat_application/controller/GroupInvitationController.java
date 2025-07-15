package com.example.chat_application.controller;

import com.example.chat_application.entity.Group;
import com.example.chat_application.entity.GroupInvitation;
import com.example.chat_application.entity.User;
import com.example.chat_application.service.GroupInvitationService;
import com.example.chat_application.service.GroupService;
import com.example.chat_application.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/invitations")
public class GroupInvitationController {

    @Autowired
    private GroupInvitationService invitationService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private UserService userService;

    // 發送邀請
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendInvitation(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            User inviter = userService.findByUsername(username).orElse(null);

            if (inviter == null) {
                return ResponseEntity.status(401).body(Map.of("error", "用戶未認證"));
            }

            Long groupId = Long.valueOf(request.get("groupId").toString());
            String inviteeUsername = request.get("inviteeUsername").toString();
            String message = request.getOrDefault("message", "").toString();

            User invitee = userService.findByUsername(inviteeUsername).orElse(null);
            if (invitee == null) {
                return ResponseEntity.status(404).body(Map.of("error", "被邀請用戶不存在"));
            }

            GroupInvitation invitation = invitationService.sendInvitation(groupId, inviter, invitee, message);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "邀請已發送給 " + invitee.getDisplayName());
            response.put("invitationId", invitation.getId());
            response.put("inviteeId", invitee.getId());
            response.put("inviteeName", invitee.getDisplayName());

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "發送邀請失敗: " + e.getMessage()));
        }
    }

    // 接受邀請
    @PostMapping("/{invitationId}/accept")
    public ResponseEntity<Map<String, Object>> acceptInvitation(
            @PathVariable Long invitationId,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userService.findByUsername(username).orElse(null);

            if (user == null) {
                return ResponseEntity.status(401).body(Map.of("error", "用戶未認證"));
            }

            boolean success = invitationService.acceptInvitation(invitationId, user);

            if (success) {
                // 獲取邀請詳情以返回群組資訊
                Optional<GroupInvitation> invitationOpt = invitationService.getInvitationById(invitationId);
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "已成功加入群組");

                if (invitationOpt.isPresent()) {
                    Group group = invitationOpt.get().getGroup();
                    response.put("groupId", group.getId());
                    response.put("groupName", group.getName());
                }

                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(500).body(Map.of("error", "接受邀請失敗"));
            }

        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "接受邀請失敗: " + e.getMessage()));
        }
    }

    // 拒絕邀請
    @PostMapping("/{invitationId}/decline")
    public ResponseEntity<Map<String, Object>> declineInvitation(
            @PathVariable Long invitationId,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userService.findByUsername(username).orElse(null);

            if (user == null) {
                return ResponseEntity.status(401).body(Map.of("error", "用戶未認證"));
            }

            boolean success = invitationService.declineInvitation(invitationId, user);

            if (success) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "已拒絕邀請"
                ));
            } else {
                return ResponseEntity.status(500).body(Map.of("error", "拒絕邀請失敗"));
            }

        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "拒絕邀請失敗: " + e.getMessage()));
        }
    }

    // 取消邀請
    @PostMapping("/{invitationId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelInvitation(
            @PathVariable Long invitationId,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userService.findByUsername(username).orElse(null);

            if (user == null) {
                return ResponseEntity.status(401).body(Map.of("error", "用戶未認證"));
            }

            boolean success = invitationService.cancelInvitation(invitationId, user);

            if (success) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "邀請已取消"
                ));
            } else {
                return ResponseEntity.status(500).body(Map.of("error", "取消邀請失敗"));
            }

        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "取消邀請失敗: " + e.getMessage()));
        }
    }

    // 獲取收到的邀請列表
    @GetMapping("/received")
    public ResponseEntity<List<Map<String, Object>>> getReceivedInvitations(
            Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userService.findByUsername(username).orElse(null);

            if (user == null) {
                return ResponseEntity.status(401).build();
            }

            List<GroupInvitation> invitations = invitationService.getReceivedInvitations(user);

            List<Map<String, Object>> invitationList = invitations.stream()
                    .map(this::mapInvitationToResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(invitationList);

        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    // 獲取待處理的邀請
    @GetMapping("/received/pending")
    public ResponseEntity<List<Map<String, Object>>> getPendingReceivedInvitations(
            Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userService.findByUsername(username).orElse(null);

            if (user == null) {
                return ResponseEntity.status(401).build();
            }

            List<GroupInvitation> invitations = invitationService.getPendingReceivedInvitations(user);

            List<Map<String, Object>> invitationList = invitations.stream()
                    .map(this::mapInvitationToResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(invitationList);

        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    // 獲取發出的邀請列表
    @GetMapping("/sent")
    public ResponseEntity<List<Map<String, Object>>> getSentInvitations(
            Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userService.findByUsername(username).orElse(null);

            if (user == null) {
                return ResponseEntity.status(401).build();
            }

            List<GroupInvitation> invitations = invitationService.getSentInvitations(user);

            List<Map<String, Object>> invitationList = invitations.stream()
                    .map(this::mapInvitationToResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(invitationList);

        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    // 獲取群組邀請列表
    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<Map<String, Object>>> getGroupInvitations(
            @PathVariable Long groupId,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userService.findByUsername(username).orElse(null);

            if (user == null) {
                return ResponseEntity.status(401).build();
            }

            Optional<Group> groupOpt = groupService.findById(groupId);
            if (groupOpt.isEmpty()) {
                return ResponseEntity.status(404).build();
            }

            Group group = groupOpt.get();

            // 只有群組成員可以查看邀請列表
            if (!group.isMember(user)) {
                return ResponseEntity.status(403).build();
            }

            List<GroupInvitation> invitations = invitationService.getGroupInvitations(group);

            List<Map<String, Object>> invitationList = invitations.stream()
                    .map(this::mapInvitationToResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(invitationList);

        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    // 獲取邀請統計
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getInvitationStats(
            Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userService.findByUsername(username).orElse(null);

            if (user == null) {
                return ResponseEntity.status(401).build();
            }

            GroupInvitationService.InvitationStats stats = invitationService.getInvitationStats(user);

            Map<String, Object> response = new HashMap<>();
            response.put("pendingCount", stats.getPendingCount());
            response.put("acceptedCount", stats.getAcceptedCount());
            response.put("declinedCount", stats.getDeclinedCount());
            response.put("totalCount", stats.getTotalCount());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    // 搜尋用戶（用於邀請）
    @GetMapping("/search-users")
    public ResponseEntity<List<Map<String, Object>>> searchUsers(
            @RequestParam String query,
            @RequestParam Long groupId,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            User currentUser = userService.findByUsername(username).orElse(null);

            if (currentUser == null) {
                return ResponseEntity.status(401).build();
            }

            Optional<Group> groupOpt = groupService.findById(groupId);
            if (groupOpt.isEmpty()) {
                return ResponseEntity.status(404).build();
            }

            Group group = groupOpt.get();

            // 只有群組成員可以搜尋用戶來邀請
            if (!group.isMember(currentUser)) {
                return ResponseEntity.status(403).build();
            }

            List<User> users = userService.searchUsers(query);

            // 過濾掉已經是群組成員的用戶和當前用戶
            List<Map<String, Object>> userList = users.stream()
                    .filter(user -> !group.isMember(user) && !user.equals(currentUser))
                    .limit(10) // 限制搜尋結果數量
                    .map(user -> {
                        Map<String, Object> userInfo = new HashMap<>();
                        userInfo.put("id", user.getId());
                        userInfo.put("username", user.getUsername());
                        userInfo.put("displayName", user.getDisplayName());
                        userInfo.put("avatarUrl", user.getAvatarUrl() != null ?
                                "/uploads/avatars/" + user.getAvatarUrl().substring(user.getAvatarUrl().lastIndexOf("/") + 1) :
                                "/images/default-avatar.png");
                        userInfo.put("isOnline", user.isOnline());
                        return userInfo;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(userList);

        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    // 輔助方法：將邀請轉換為回應格式
    private Map<String, Object> mapInvitationToResponse(GroupInvitation invitation) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", invitation.getId());
        response.put("status", invitation.getStatus().toString());
        response.put("message", invitation.getMessage());
        response.put("createdAt", invitation.getCreatedAt().toString());
        response.put("respondedAt", invitation.getRespondedAt() != null ?
                invitation.getRespondedAt().toString() : null);
        response.put("expiresAt", invitation.getExpiresAt().toString());
        response.put("isExpired", invitation.isExpired());
        response.put("isPending", invitation.isPending());

        // 群組資訊
        Group group = invitation.getGroup();
        Map<String, Object> groupInfo = new HashMap<>();
        groupInfo.put("id", group.getId());
        groupInfo.put("name", group.getName());
        groupInfo.put("description", group.getDescription());
        groupInfo.put("avatarUrl", group.getAvatarUrl() != null ?
                "/uploads/groups/" + group.getAvatarUrl().substring(group.getAvatarUrl().lastIndexOf("/") + 1) :
                "/images/default-group.png");
        groupInfo.put("memberCount", group.getMembers().size());
        response.put("group", groupInfo);

        // 邀請者資訊
        User inviter = invitation.getInviter();
        Map<String, Object> inviterInfo = new HashMap<>();
        inviterInfo.put("id", inviter.getId());
        inviterInfo.put("username", inviter.getUsername());
        inviterInfo.put("displayName", inviter.getDisplayName());
        inviterInfo.put("avatarUrl", inviter.getAvatarUrl() != null ?
                "/uploads/avatars/" + inviter.getAvatarUrl().substring(inviter.getAvatarUrl().lastIndexOf("/") + 1) :
                "/images/default-avatar.png");
        response.put("inviter", inviterInfo);

        // 被邀請者資訊
        User invitee = invitation.getInvitee();
        Map<String, Object> inviteeInfo = new HashMap<>();
        inviteeInfo.put("id", invitee.getId());
        inviteeInfo.put("username", invitee.getUsername());
        inviteeInfo.put("displayName", invitee.getDisplayName());
        inviteeInfo.put("avatarUrl", invitee.getAvatarUrl() != null ?
                "/uploads/avatars/" + invitee.getAvatarUrl().substring(invitee.getAvatarUrl().lastIndexOf("/") + 1) :
                "/images/default-avatar.png");
        response.put("invitee", inviteeInfo);

        return response;
    }
}