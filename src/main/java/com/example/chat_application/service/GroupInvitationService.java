package com.example.chat_application.service;

import com.example.chat_application.entity.Group;
import com.example.chat_application.entity.GroupInvitation;
import com.example.chat_application.entity.Message;
import com.example.chat_application.entity.User;
import com.example.chat_application.repository.GroupInvitationRepository;
import com.example.chat_application.repository.GroupRepository;
import com.example.chat_application.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class GroupInvitationService {

    @Autowired
    private GroupInvitationRepository invitationRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private MessageRepository messageRepository;

    // 發送邀請
    @Transactional
    public GroupInvitation sendInvitation(Long groupId, User inviter, User invitee, String message) {
        // 驗證群組存在
        Optional<Group> groupOpt = groupRepository.findById(groupId);
        if (groupOpt.isEmpty()) {
            throw new RuntimeException("群組不存在");
        }

        Group group = groupOpt.get();

        // 驗證邀請者是否為群組成員（通常只有成員可以邀請）
        if (!group.isMember(inviter)) {
            throw new RuntimeException("只有群組成員可以邀請其他用戶");
        }

        // 檢查被邀請者是否已經是群組成員
        if (group.isMember(invitee)) {
            throw new RuntimeException("該用戶已經是群組成員");
        }

        // 檢查是否已經有待處理的邀請
        Optional<GroupInvitation> existingInvitation = invitationRepository
                .findExistingPendingInvitation(group, invitee, LocalDateTime.now());

        if (existingInvitation.isPresent()) {
            throw new RuntimeException("該用戶已經有待處理的邀請");
        }

        // 檢查群組是否已滿
        if (group.getMembers().size() >= group.getMaxMembers()) {
            throw new RuntimeException("群組已滿，無法邀請新成員");
        }

        // 創建邀請
        GroupInvitation invitation = new GroupInvitation(group, inviter, invitee, message);
        GroupInvitation savedInvitation = invitationRepository.save(invitation);

        // 記錄邀請事件到群組訊息中（可選）
        Message systemMessage = new Message(group,
                inviter.getDisplayName() + " 邀請了 " + invitee.getDisplayName() + " 加入群組",
                Message.MessageType.SYSTEM);
        messageRepository.save(systemMessage);

        return savedInvitation;
    }

    // 接受邀請
    @Transactional
    public boolean acceptInvitation(Long invitationId, User user) {
        Optional<GroupInvitation> invitationOpt = invitationRepository.findById(invitationId);
        if (invitationOpt.isEmpty()) {
            throw new RuntimeException("邀請不存在");
        }

        GroupInvitation invitation = invitationOpt.get();

        // 驗證是否為被邀請者
        if (!invitation.getInvitee().equals(user)) {
            throw new RuntimeException("您無權處理此邀請");
        }

        // 檢查邀請狀態
        if (!invitation.isPending()) {
            throw new RuntimeException("邀請已過期或已被處理");
        }

        // 再次檢查群組是否已滿
        Group group = invitation.getGroup();
        if (group.getMembers().size() >= group.getMaxMembers()) {
            throw new RuntimeException("群組已滿，無法加入");
        }

        // 檢查是否已經是成員（防止併發問題）
        if (group.isMember(user)) {
            throw new RuntimeException("您已經是群組成員");
        }

        // 接受邀請
        invitation.accept();
        invitationRepository.save(invitation);

        // 將用戶添加到群組
        group.addMember(user);
        groupRepository.save(group);

        // 創建系統訊息
        Message systemMessage = new Message(group,
                user.getDisplayName() + " 通過邀請加入了群組",
                Message.MessageType.SYSTEM);
        messageRepository.save(systemMessage);

        return true;
    }

    // 拒絕邀請
    @Transactional
    public boolean declineInvitation(Long invitationId, User user) {
        Optional<GroupInvitation> invitationOpt = invitationRepository.findById(invitationId);
        if (invitationOpt.isEmpty()) {
            throw new RuntimeException("邀請不存在");
        }

        GroupInvitation invitation = invitationOpt.get();

        // 驗證是否為被邀請者
        if (!invitation.getInvitee().equals(user)) {
            throw new RuntimeException("您無權處理此邀請");
        }

        // 檢查邀請狀態
        if (!invitation.isPending()) {
            throw new RuntimeException("邀請已過期或已被處理");
        }

        // 拒絕邀請
        invitation.decline();
        invitationRepository.save(invitation);

        return true;
    }

    // 取消邀請（邀請者或群組管理員）
    @Transactional
    public boolean cancelInvitation(Long invitationId, User user) {
        Optional<GroupInvitation> invitationOpt = invitationRepository.findById(invitationId);
        if (invitationOpt.isEmpty()) {
            throw new RuntimeException("邀請不存在");
        }

        GroupInvitation invitation = invitationOpt.get();
        Group group = invitation.getGroup();

        // 驗證權限（邀請者或群組創建者）
        if (!invitation.getInviter().equals(user) && !group.isCreator(user)) {
            throw new RuntimeException("您無權取消此邀請");
        }

        // 檢查邀請狀態
        if (!invitation.isPending()) {
            throw new RuntimeException("邀請已過期或已被處理，無法取消");
        }

        // 取消邀請
        invitation.cancel();
        invitationRepository.save(invitation);

        return true;
    }

    // 獲取用戶收到的邀請
    public List<GroupInvitation> getReceivedInvitations(User user) {
        return invitationRepository.findByInvitee(user);
    }

    // 獲取用戶收到的待處理邀請
    public List<GroupInvitation> getPendingReceivedInvitations(User user) {
        return invitationRepository.findPendingInvitationsByInvitee(user, LocalDateTime.now());
    }

    // 獲取用戶發出的邀請
    public List<GroupInvitation> getSentInvitations(User user) {
        return invitationRepository.findByInviter(user);
    }

    // 獲取群組的邀請
    public List<GroupInvitation> getGroupInvitations(Group group) {
        return invitationRepository.findByGroup(group);
    }

    // 獲取群組的待處理邀請
    public List<GroupInvitation> getPendingGroupInvitations(Group group) {
        return invitationRepository.findPendingInvitationsByGroup(group, LocalDateTime.now());
    }

    // 清理過期邀請（定時任務）
    @Transactional
    public int cleanupExpiredInvitations() {
        List<GroupInvitation> expiredInvitations = invitationRepository
                .findExpiredInvitations(LocalDateTime.now());

        for (GroupInvitation invitation : expiredInvitations) {
            invitation.markExpired();
        }

        if (!expiredInvitations.isEmpty()) {
            invitationRepository.saveAll(expiredInvitations);
        }

        return expiredInvitations.size();
    }

    // 獲取邀請詳情
    public Optional<GroupInvitation> getInvitationById(Long invitationId) {
        return invitationRepository.findById(invitationId);
    }

    // 檢查用戶是否可以邀請其他用戶到指定群組
    public boolean canUserInviteToGroup(User user, Group group) {
        // 只有群組成員可以邀請
        return group.isMember(user);
    }

    // 獲取邀請統計
    public InvitationStats getInvitationStats(User user) {
        long pendingReceived = invitationRepository.countByInviteeAndStatus(user, GroupInvitation.InvitationStatus.PENDING);
        long acceptedReceived = invitationRepository.countByInviteeAndStatus(user, GroupInvitation.InvitationStatus.ACCEPTED);
        long declinedReceived = invitationRepository.countByInviteeAndStatus(user, GroupInvitation.InvitationStatus.DECLINED);

        return new InvitationStats(pendingReceived, acceptedReceived, declinedReceived);
    }

    // 邀請統計類
    public static class InvitationStats {
        private final long pendingCount;
        private final long acceptedCount;
        private final long declinedCount;

        public InvitationStats(long pendingCount, long acceptedCount, long declinedCount) {
            this.pendingCount = pendingCount;
            this.acceptedCount = acceptedCount;
            this.declinedCount = declinedCount;
        }

        public long getPendingCount() { return pendingCount; }
        public long getAcceptedCount() { return acceptedCount; }
        public long getDeclinedCount() { return declinedCount; }
        public long getTotalCount() { return pendingCount + acceptedCount + declinedCount; }
    }
}