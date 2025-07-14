package com.example.chat_application.service;

import com.example.chat_application.entity.Group;
import com.example.chat_application.entity.Message;
import com.example.chat_application.entity.User;
import com.example.chat_application.repository.GroupRepository;
import com.example.chat_application.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GroupService {

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private MessageRepository messageRepository;

    // 群組頭像存儲路徑
    private final String GROUP_AVATAR_UPLOAD_DIR = "uploads/groups/";

    // 創建群組
    public Group createGroup(String name, String description, User creator, boolean isPrivate) {
        Group group = new Group(name, description, creator);
        group.setPrivate(isPrivate);

        Group savedGroup = groupRepository.save(group);

        // 創建系統訊息：群組創建
        Message systemMessage = new Message(savedGroup,
                creator.getDisplayName() + " 創建了群組",
                Message.MessageType.SYSTEM);
        messageRepository.save(systemMessage);

        return savedGroup;
    }

    // 加入群組
    public boolean joinGroup(Long groupId, User user) {
        Optional<Group> groupOpt = groupRepository.findById(groupId);
        if (groupOpt.isPresent()) {
            Group group = groupOpt.get();

            // 檢查群組是否已滿
            if (group.getMembers().size() >= group.getMaxMembers()) {
                throw new RuntimeException("群組已滿，無法加入");
            }

            // 檢查是否已經是成員
            if (group.isMember(user)) {
                throw new RuntimeException("您已經是群組成員");
            }

            group.addMember(user);
            groupRepository.save(group);

            // 創建系統訊息：用戶加入
            Message systemMessage = new Message(group,
                    user.getDisplayName() + " 加入了群組",
                    Message.MessageType.SYSTEM);
            messageRepository.save(systemMessage);

            return true;
        }
        return false;
    }

    // 離開群組
    public boolean leaveGroup(Long groupId, User user) {
        Optional<Group> groupOpt = groupRepository.findById(groupId);
        if (groupOpt.isPresent()) {
            Group group = groupOpt.get();

            // 創建者不能離開群組（需要先轉移群組或解散群組）
            if (group.isCreator(user)) {
                throw new RuntimeException("群組創建者無法離開群組，請先轉移群組所有權或解散群組");
            }

            group.removeMember(user);
            groupRepository.save(group);

            // 創建系統訊息：用戶離開
            Message systemMessage = new Message(group,
                    user.getDisplayName() + " 離開了群組",
                    Message.MessageType.SYSTEM);
            messageRepository.save(systemMessage);

            return true;
        }
        return false;
    }

    // 轉移群組所有權
    public boolean transferOwnership(Long groupId, User currentOwner, User newOwner) {
        Optional<Group> groupOpt = groupRepository.findById(groupId);
        if (groupOpt.isPresent()) {
            Group group = groupOpt.get();

            // 檢查當前用戶是否為群組創建者
            if (!group.isCreator(currentOwner)) {
                throw new RuntimeException("只有群組創建者可以轉移所有權");
            }

            // 檢查新所有者是否為群組成員
            if (!group.isMember(newOwner)) {
                throw new RuntimeException("新所有者必須是群組成員");
            }

            group.setCreator(newOwner);
            groupRepository.save(group);

            // 創建系統訊息：轉移所有權
            Message systemMessage = new Message(group,
                    currentOwner.getDisplayName() + " 將群組所有權轉移給了 " + newOwner.getDisplayName(),
                    Message.MessageType.SYSTEM);
            messageRepository.save(systemMessage);

            return true;
        }
        return false;
    }

    // 解散群組
    public boolean dissolveGroup(Long groupId, User user) {
        Optional<Group> groupOpt = groupRepository.findById(groupId);
        if (groupOpt.isPresent()) {
            Group group = groupOpt.get();

            // 只有創建者可以解散群組
            if (!group.isCreator(user)) {
                throw new RuntimeException("只有群組創建者可以解散群組");
            }

            // 刪除群組頭像
            if (group.getAvatarUrl() != null) {
                try {
                    Path avatarPath = Paths.get(group.getAvatarUrl());
                    Files.deleteIfExists(avatarPath);
                } catch (IOException e) {
                    System.err.println("刪除群組頭像失敗: " + e.getMessage());
                }
            }

            groupRepository.delete(group);
            return true;
        }
        return false;
    }

    // 踢出成員
    public boolean removeMember(Long groupId, User admin, User memberToRemove) {
        Optional<Group> groupOpt = groupRepository.findById(groupId);
        if (groupOpt.isPresent()) {
            Group group = groupOpt.get();

            // 只有創建者可以踢出成員
            if (!group.isCreator(admin)) {
                throw new RuntimeException("只有群組創建者可以移除成員");
            }

            // 不能踢出自己
            if (admin.equals(memberToRemove)) {
                throw new RuntimeException("無法移除自己");
            }

            group.removeMember(memberToRemove);
            groupRepository.save(group);

            // 創建系統訊息：成員被移除
            Message systemMessage = new Message(group,
                    memberToRemove.getDisplayName() + " 被移出了群組",
                    Message.MessageType.SYSTEM);
            messageRepository.save(systemMessage);

            return true;
        }
        return false;
    }

    // 更新群組資訊
    public Group updateGroup(Long groupId, User user, String name, String description, Boolean isPrivate) {
        Optional<Group> groupOpt = groupRepository.findById(groupId);
        if (groupOpt.isPresent()) {
            Group group = groupOpt.get();

            // 只有創建者可以修改群組資訊
            if (!group.isCreator(user)) {
                throw new RuntimeException("只有群組創建者可以修改群組資訊");
            }

            if (name != null && !name.trim().isEmpty()) {
                group.setName(name.trim());
            }
            if (description != null) {
                group.setDescription(description.trim());
            }
            if (isPrivate != null) {
                group.setPrivate(isPrivate);
            }

            return groupRepository.save(group);
        }
        throw new RuntimeException("群組不存在");
    }

    // 上傳群組頭像
    public String uploadGroupAvatar(Long groupId, User user, MultipartFile file) throws IOException {
        Optional<Group> groupOpt = groupRepository.findById(groupId);
        if (groupOpt.isPresent()) {
            Group group = groupOpt.get();

            // 只有創建者可以修改群組頭像
            if (!group.isCreator(user)) {
                throw new RuntimeException("只有群組創建者可以修改群組頭像");
            }

            if (file.isEmpty()) {
                throw new RuntimeException("檔案不能為空");
            }

            // 檢查檔案類型
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new RuntimeException("只能上傳圖片檔案");
            }

            // 創建上傳目錄
            Path uploadPath = Paths.get(GROUP_AVATAR_UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // 生成唯一檔案名
            String originalFilename = file.getOriginalFilename();
            String fileExtension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String fileName = "group_" + groupId + "_" + UUID.randomUUID().toString() + fileExtension;

            // 儲存檔案
            Path filePath = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), filePath);

            // 刪除舊頭像
            if (group.getAvatarUrl() != null && !group.getAvatarUrl().isEmpty()) {
                try {
                    Path oldAvatarPath = Paths.get(group.getAvatarUrl());
                    Files.deleteIfExists(oldAvatarPath);
                } catch (IOException e) {
                    System.err.println("無法刪除舊群組頭像: " + e.getMessage());
                }
            }

            String avatarUrl = GROUP_AVATAR_UPLOAD_DIR + fileName;
            group.setAvatarUrl(avatarUrl);
            groupRepository.save(group);

            return avatarUrl;
        }
        throw new RuntimeException("群組不存在");
    }

    // 查詢方法
    @Transactional(readOnly = true)
    public Optional<Group> findById(Long id) {
        return findByIdWithMembers(id);
    }

    public List<Group> findByUser(User user) {
        return groupRepository.findByMembersContaining(user);
    }

    public List<Group> findCreatedByUser(User user) {
        return groupRepository.findByCreator(user);
    }

    public List<Group> findPublicGroups() {
        return groupRepository.findPublicGroups();
    }

    public List<Group> searchPublicGroups(String search) {
        return groupRepository.searchPublicGroups(search);
    }

    public List<Group> findAvailableGroups(User user) {
        return groupRepository.findPublicGroupsNotJoinedByUser(user);
    }

    // 獲取群組統計資訊
    public GroupStats getGroupStats(Group group) {
        long messageCount = messageRepository.findByGroupOrderByTimestamp(group).size();
        int memberCount = group.getMembers().size();

        return new GroupStats(memberCount, messageCount, group.getCreatedAt());
    }

    // 群組統計資訊類
    public static class GroupStats {
        private final int memberCount;
        private final long messageCount;
        private final java.time.LocalDateTime createdAt;

        public GroupStats(int memberCount, long messageCount, java.time.LocalDateTime createdAt) {
            this.memberCount = memberCount;
            this.messageCount = messageCount;
            this.createdAt = createdAt;
        }

        // Getters
        public int getMemberCount() { return memberCount; }
        public long getMessageCount() { return messageCount; }
        public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    }

    @Transactional(readOnly = true)
    public Optional<Group> findByIdWithMembers(Long id) {
        Optional<Group> groupOpt = groupRepository.findById(id);
        if (groupOpt.isPresent()) {
            Group group = groupOpt.get();
            // 強制初始化 members 集合
            group.getMembers().size();
            return Optional.of(group);
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public List<Group> findByUserWithMembers(User user) {
        List<Group> groups = groupRepository.findByMembersContaining(user);
        // 強制初始化每個群組的 members 集合
        groups.forEach(group -> group.getMembers().size());
        return groups;
    }

    // 添加檢查用戶是否為群組成員的方法
    @Transactional(readOnly = true)
    public boolean isUserMemberOfGroup(Long groupId, User user) {
        Optional<Group> groupOpt = groupRepository.findById(groupId);
        if (groupOpt.isPresent()) {
            Group group = groupOpt.get();
            // 在事務內檢查成員資格
            return group.getMembers().contains(user);
        }
        return false;
    }
}
