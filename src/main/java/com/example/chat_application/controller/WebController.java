package com.example.chat_application.controller;

import com.example.chat_application.entity.Group;
import com.example.chat_application.entity.GroupInvitation;
import com.example.chat_application.entity.Message;
import com.example.chat_application.entity.User;
import com.example.chat_application.service.GroupInvitationService;
import com.example.chat_application.service.GroupService;
import com.example.chat_application.service.MessageService;
import com.example.chat_application.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
public class WebController {

    @Autowired
    private UserService userService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private GroupInvitationService invitationService;

//    @GetMapping("/")
//    public String index() {
//        return "redirect:/login";
//    }

    // 首頁重定向到登入或主頁
    @GetMapping("/")
    public String index(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            return "redirect:/home";
        }
        return "redirect:/login";
    }

    // 主頁（新增）
    @GetMapping("/home")
    public String home(Authentication authentication, Model model) {
        String username = authentication.getName();
        User currentUser = userService.findByUsername(username).orElse(null);

        if (currentUser != null) {
            userService.setUserOnline(username, true);

            // 獲取用戶的群組
            List<Group> userGroups = groupService.findByUser(currentUser);

            // 獲取最近的私人聊天夥伴
            List<User> chatPartners = messageService.getChatPartners(currentUser);

            // 獲取公開群組（推薦加入）
            List<Group> recommendedGroups = groupService.findAvailableGroups(currentUser)
                    .stream()
                    .limit(5) // 只顯示前5個
                    .toList();

            // 獲取最新訊息預覽
            Map<Long, Message> latestGroupMessages = messageService.getLatestGroupMessages(currentUser);

            model.addAttribute("currentUser", currentUser);
            model.addAttribute("userGroups", userGroups);
            model.addAttribute("chatPartners", chatPartners);
            model.addAttribute("recommendedGroups", recommendedGroups);
            model.addAttribute("latestGroupMessages", latestGroupMessages);
        }

        return "home";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(@RequestParam String username,
                               @RequestParam String password,
                               @RequestParam String displayName,
                               Model model) {
        try {
            userService.registerUser(username, password, displayName);
            return "redirect:/login?registered";
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            return "register";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null) {
            // 設置用戶為離線狀態
            try {
                userService.setUserOnline(authentication.getName(), false);
            } catch (Exception e) {
                // 如果設置離線狀態失敗，記錄但不影響登出流程
                System.err.println("設置用戶離線狀態失敗: " + e.getMessage());
            }

            // 執行登出
            new SecurityContextLogoutHandler().logout(request, response, authentication);
        }

        return "redirect:/login?logout";
    }

    @GetMapping("/chat")
    public String chat(Authentication authentication, Model model) {
        String username = authentication.getName();
        User currentUser = userService.findByUsername(username).orElse(null);

        if (currentUser != null) {
            userService.setUserOnline(username, true);
            model.addAttribute("currentUser", currentUser);
            model.addAttribute("users", userService.getAllUsers());
            model.addAttribute("chatType", "private");
        }

        return "chat";
    }

    // 群組聊天室
    @GetMapping("/group/{groupId}")
    public String groupChat(@PathVariable Long groupId,
                            Authentication authentication,
                            Model model,
                            RedirectAttributes redirectAttributes) {
        String username = authentication.getName();
        User currentUser = userService.findByUsername(username).orElse(null);

        if (currentUser == null) {
            return "redirect:/login";
        }

        // 使用帶有成員初始化的查詢方法
        Optional<Group> groupOpt = groupService.findByIdWithMembers(groupId);
        if (groupOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "群組不存在");
            return "redirect:/home";
        }

        Group group = groupOpt.get();

        // 檢查用戶是否為群組成員（現在 members 已經被初始化）
        if (!group.isMember(currentUser)) {
            redirectAttributes.addFlashAttribute("error", "您不是該群組的成員");
            return "redirect:/home";
        }

        userService.setUserOnline(username, true);

        // 修復頭像URL（現在可以安全地遍歷 members）
        group.getMembers().forEach(member -> {
            String avatarUrl = member.getAvatarUrl();
            if (avatarUrl == null || avatarUrl.trim().isEmpty()) {
                member.setAvatarUrl("/images/default-avatar.png");
            } else if (!avatarUrl.startsWith("/uploads/") && !avatarUrl.startsWith("/images/")) {
                member.setAvatarUrl("/uploads/avatars/" + avatarUrl);
            }
        });

        // 設置群組頭像
        String groupAvatarUrl = group.getAvatarUrl();
        if (groupAvatarUrl == null || groupAvatarUrl.trim().isEmpty()) {
            group.setAvatarUrl("/images/default-group.png");
        } else if (!groupAvatarUrl.startsWith("/uploads/") && !groupAvatarUrl.startsWith("/images/")) {
            group.setAvatarUrl("/uploads/groups/" + groupAvatarUrl);
        }

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("group", group);
        model.addAttribute("chatType", "group");

        return "chat";
    }

    // 群組管理頁面
    @GetMapping("/groups")
    public String groups(Authentication authentication, Model model) {
        String username = authentication.getName();
        User currentUser = userService.findByUsername(username).orElse(null);

        if (currentUser != null) {
            List<Group> userGroups = groupService.findByUser(currentUser);
            List<Group> createdGroups = groupService.findCreatedByUser(currentUser);
            List<Group> publicGroups = groupService.findPublicGroups()
                    .stream()
                    .limit(10) // 限制顯示數量
                    .toList();

            model.addAttribute("currentUser", currentUser);
            model.addAttribute("userGroups", userGroups);
            model.addAttribute("createdGroups", createdGroups);
            model.addAttribute("publicGroups", publicGroups);
        }

        return "groups";
    }

    // 創建群組頁面
    @GetMapping("/groups/create")
    public String createGroupPage(Authentication authentication, Model model) {
        String username = authentication.getName();
        User currentUser = userService.findByUsername(username).orElse(null);

        if (currentUser != null) {
            model.addAttribute("currentUser", currentUser);
        }

        return "create-group";
    }

    // 處理創建群組
    @PostMapping("/groups/create")
    public String createGroup(@RequestParam String name,
                              @RequestParam(required = false) String description,
                              @RequestParam(required = false, defaultValue = "false") boolean isPrivate,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        try {
            String username = authentication.getName();
            User currentUser = userService.findByUsername(username).orElse(null);

            if (currentUser == null) {
                return "redirect:/login";
            }

            Group group = groupService.createGroup(name, description, currentUser, isPrivate);
            redirectAttributes.addFlashAttribute("success", "群組創建成功！");
            return "redirect:/group/" + group.getId();

        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/groups/create";
        }
    }

    // 加入群組
    @PostMapping("/groups/{groupId}/join")
    public String joinGroup(@PathVariable Long groupId,
                            Authentication authentication,
                            RedirectAttributes redirectAttributes) {
        try {
            String username = authentication.getName();
            User currentUser = userService.findByUsername(username).orElse(null);

            if (currentUser == null) {
                return "redirect:/login";
            }

            groupService.joinGroup(groupId, currentUser);
            redirectAttributes.addFlashAttribute("success", "成功加入群組！");
            return "redirect:/group/" + groupId;

        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/groups";
        }
    }

    // 離開群組
    @PostMapping("/groups/{groupId}/leave")
    public String leaveGroup(@PathVariable Long groupId,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            String username = authentication.getName();
            User currentUser = userService.findByUsername(username).orElse(null);

            if (currentUser == null) {
                return "redirect:/login";
            }

            groupService.leaveGroup(groupId, currentUser);
            redirectAttributes.addFlashAttribute("success", "已離開群組");
            return "redirect:/groups";

        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/group/" + groupId;
        }
    }

    // 群組設定頁面
    @GetMapping("/groups/{groupId}/settings")
    public String groupSettings(@PathVariable Long groupId,
                                Authentication authentication,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        String username = authentication.getName();
        User currentUser = userService.findByUsername(username).orElse(null);

        if (currentUser == null) {
            return "redirect:/login";
        }

        Optional<Group> groupOpt = groupService.findById(groupId);
        if (groupOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "群組不存在");
            return "redirect:/groups";
        }

        Group group = groupOpt.get();

        // 只有創建者可以訪問設定頁面
        if (!group.isCreator(currentUser)) {
            redirectAttributes.addFlashAttribute("error", "只有群組創建者可以修改設定");
            return "redirect:/group/" + groupId;
        }

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("group", group);
        model.addAttribute("groupStats", groupService.getGroupStats(group));

        return "group-settings";
    }

    @PostMapping("/groups/{groupId}/dissolve")
    public String dissolveGroupWeb(@PathVariable Long groupId,
                                   Authentication authentication,
                                   RedirectAttributes redirectAttributes) {
        try {
            String username = authentication.getName();
            User currentUser = userService.findByUsername(username).orElse(null);

            if (currentUser == null) {
                return "redirect:/login";
            }

            // 獲取群組名稱（用於成功訊息）
            Optional<Group> groupOpt = groupService.findById(groupId);
            String groupName = groupOpt.map(Group::getName).orElse("未知群組");

            boolean success = groupService.dissolveGroup(groupId, currentUser);

            if (success) {
                redirectAttributes.addFlashAttribute("success",
                        "群組「" + groupName + "」已成功解散");
            } else {
                redirectAttributes.addFlashAttribute("error", "解散群組失敗");
            }

            return "redirect:/groups";

        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/groups/" + groupId + "/settings";
        }
    }

    // 更新群組資訊
    @PostMapping("/groups/{groupId}/update")
    public String updateGroup(@PathVariable Long groupId,
                              @RequestParam String name,
                              @RequestParam(required = false) String description,
                              @RequestParam(required = false) Boolean isPrivate,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        try {
            String username = authentication.getName();
            User currentUser = userService.findByUsername(username).orElse(null);

            if (currentUser == null) {
                return "redirect:/login";
            }

            groupService.updateGroup(groupId, currentUser, name, description, isPrivate);
            redirectAttributes.addFlashAttribute("success", "群組資訊更新成功！");
            return "redirect:/groups/" + groupId + "/settings";

        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/groups/" + groupId + "/settings";
        }
    }

    // 上傳群組頭像
    @PostMapping("/groups/{groupId}/upload-avatar")
    public String uploadGroupAvatar(@PathVariable Long groupId,
                                    @RequestParam("avatar") MultipartFile file,
                                    Authentication authentication,
                                    RedirectAttributes redirectAttributes) {
        try {
            String username = authentication.getName();
            User currentUser = userService.findByUsername(username).orElse(null);

            if (currentUser == null) {
                return "redirect:/login";
            }

            groupService.uploadGroupAvatar(groupId, currentUser, file);
            redirectAttributes.addFlashAttribute("success", "群組頭像上傳成功！");
            return "redirect:/groups/" + groupId + "/settings";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "頭像上傳失敗：" + e.getMessage());
            return "redirect:/groups/" + groupId + "/settings";
        }
    }

    // 個人資料頁面
    @GetMapping("/profile")
    public String profile(Authentication authentication, Model model) {
        String username = authentication.getName();
        User currentUser = userService.findByUsername(username).orElse(null);

        if (currentUser != null) {
            model.addAttribute("user", currentUser);
        }

        return "profile";
    }

    // 更新個人資料
    @PostMapping("/profile/update")
    public String updateProfile(@RequestParam String displayName,
                                @RequestParam(required = false) String email,
                                @RequestParam(required = false) String phoneNumber,
                                @RequestParam(required = false) String bio,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        try {
            String username = authentication.getName();
            userService.updateProfile(username, displayName, email, phoneNumber, bio);
            redirectAttributes.addFlashAttribute("success", "個人資料更新成功！");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/profile";
    }

    // 更改密碼
    @PostMapping("/profile/change-password")
    public String changePassword(@RequestParam String oldPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        try {
            if (!newPassword.equals(confirmPassword)) {
                throw new RuntimeException("新密碼與確認密碼不一致");
            }

            String username = authentication.getName();
            userService.changePassword(username, oldPassword, newPassword);
            redirectAttributes.addFlashAttribute("success", "密碼更改成功！");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/profile";
    }

    // 上傳頭像
    @PostMapping("/profile/upload-avatar")
    public String uploadAvatar(@RequestParam("avatar") MultipartFile file,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        try {
            String username = authentication.getName();
            userService.uploadAvatar(username, file);
            redirectAttributes.addFlashAttribute("success", "頭像上傳成功！");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "頭像上傳失敗：" + e.getMessage());
        }

        return "redirect:/profile";
    }

    // 刪除頭像
    @PostMapping("/profile/delete-avatar")
    public String deleteAvatar(Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        try {
            String username = authentication.getName();
            userService.deleteAvatar(username);
            redirectAttributes.addFlashAttribute("success", "頭像刪除成功！");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/profile";
    }

    @GetMapping("/invitations")
    public String invitations(Authentication authentication, Model model) {
        String username = authentication.getName();
        User currentUser = userService.findByUsername(username).orElse(null);

        if (currentUser != null) {
            model.addAttribute("currentUser", currentUser);
        }

        return "invitations";
    }

    // 群組預覽頁面（用於邀請）
    @GetMapping("/group/{groupId}/preview")
    public String groupPreview(@PathVariable Long groupId,
                               @RequestParam(required = false) Long invitationId,
                               Authentication authentication,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        String username = authentication.getName();
        User currentUser = userService.findByUsername(username).orElse(null);

        if (currentUser == null) {
            return "redirect:/login";
        }

        // 使用帶有成員初始化的查詢方法
        Optional<Group> groupOpt = groupService.findByIdWithMembers(groupId);
        if (groupOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "群組不存在");
            return "redirect:/groups";
        }

        Group group = groupOpt.get();

        // 如果用戶已經是群組成員，直接跳轉到群組聊天
        if (group.isMember(currentUser)) {
            return "redirect:/group/" + groupId;
        }

        // 修復頭像URL
        group.getMembers().forEach(member -> {
            String avatarUrl = member.getAvatarUrl();
            if (avatarUrl == null || avatarUrl.trim().isEmpty()) {
                member.setAvatarUrl("/images/default-avatar.png");
            } else if (!avatarUrl.startsWith("/uploads/") && !avatarUrl.startsWith("/images/")) {
                member.setAvatarUrl("/uploads/avatars/" + avatarUrl);
            }
        });

        // 設置群組頭像
        String groupAvatarUrl = group.getAvatarUrl();
        if (groupAvatarUrl == null || groupAvatarUrl.trim().isEmpty()) {
            group.setAvatarUrl("/images/default-group.png");
        } else if (!groupAvatarUrl.startsWith("/uploads/") && !groupAvatarUrl.startsWith("/images/")) {
            group.setAvatarUrl("/uploads/groups/" + groupAvatarUrl);
        }

        // 如果提供了邀請ID，獲取邀請詳情
        GroupInvitation invitation = null;
        if (invitationId != null) {
            Optional<GroupInvitation> invitationOpt = invitationService.getInvitationById(invitationId);
            if (invitationOpt.isPresent()) {
                invitation = invitationOpt.get();
                // 驗證邀請是否屬於當前用戶
                if (!invitation.getInvitee().equals(currentUser)) {
                    redirectAttributes.addFlashAttribute("error", "無效的邀請");
                    return "redirect:/invitations";
                }
            }
        }

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("group", group);
        model.addAttribute("invitation", invitation);

        return "group-preview";
    }
}
