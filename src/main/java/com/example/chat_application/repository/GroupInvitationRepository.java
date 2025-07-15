package com.example.chat_application.repository;

import com.example.chat_application.entity.Group;
import com.example.chat_application.entity.GroupInvitation;
import com.example.chat_application.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface GroupInvitationRepository extends JpaRepository<GroupInvitation, Long> {

    // 查找用戶收到的邀請
    @Query("SELECT gi FROM GroupInvitation gi WHERE gi.invitee = :user ORDER BY gi.createdAt DESC")
    List<GroupInvitation> findByInvitee(@Param("user") User user);

    // 查找用戶發出的邀請
    @Query("SELECT gi FROM GroupInvitation gi WHERE gi.inviter = :user ORDER BY gi.createdAt DESC")
    List<GroupInvitation> findByInviter(@Param("user") User user);

    // 查找群組的所有邀請
    @Query("SELECT gi FROM GroupInvitation gi WHERE gi.group = :group ORDER BY gi.createdAt DESC")
    List<GroupInvitation> findByGroup(@Param("group") Group group);

    // 查找用戶收到的待處理邀請
    @Query("SELECT gi FROM GroupInvitation gi WHERE gi.invitee = :user AND gi.status = 'PENDING' AND gi.expiresAt > :now ORDER BY gi.createdAt DESC")
    List<GroupInvitation> findPendingInvitationsByInvitee(@Param("user") User user, @Param("now") LocalDateTime now);

    // 查找群組的待處理邀請
    @Query("SELECT gi FROM GroupInvitation gi WHERE gi.group = :group AND gi.status = 'PENDING' AND gi.expiresAt > :now ORDER BY gi.createdAt DESC")
    List<GroupInvitation> findPendingInvitationsByGroup(@Param("group") Group group, @Param("now") LocalDateTime now);

    // 檢查是否已經有待處理的邀請
    @Query("SELECT gi FROM GroupInvitation gi WHERE gi.group = :group AND gi.invitee = :invitee AND gi.status = 'PENDING' AND gi.expiresAt > :now")
    Optional<GroupInvitation> findExistingPendingInvitation(@Param("group") Group group, @Param("invitee") User invitee, @Param("now") LocalDateTime now);

    // 查找過期的邀請
    @Query("SELECT gi FROM GroupInvitation gi WHERE gi.status = 'PENDING' AND gi.expiresAt <= :now")
    List<GroupInvitation> findExpiredInvitations(@Param("now") LocalDateTime now);

    // 根據狀態查找邀請
    @Query("SELECT gi FROM GroupInvitation gi WHERE gi.status = :status ORDER BY gi.createdAt DESC")
    List<GroupInvitation> findByStatus(@Param("status") GroupInvitation.InvitationStatus status);

    // 查找用戶在特定群組的邀請記錄
    @Query("SELECT gi FROM GroupInvitation gi WHERE gi.group = :group AND gi.invitee = :invitee ORDER BY gi.createdAt DESC")
    List<GroupInvitation> findByGroupAndInvitee(@Param("group") Group group, @Param("invitee") User invitee);

    // 統計用戶收到的邀請數量（按狀態）
    @Query("SELECT COUNT(gi) FROM GroupInvitation gi WHERE gi.invitee = :user AND gi.status = :status")
    long countByInviteeAndStatus(@Param("user") User user, @Param("status") GroupInvitation.InvitationStatus status);

    // 統計群組的邀請數量
    @Query("SELECT COUNT(gi) FROM GroupInvitation gi WHERE gi.group = :group AND gi.status = :status")
    long countByGroupAndStatus(@Param("group") Group group, @Param("status") GroupInvitation.InvitationStatus status);
}