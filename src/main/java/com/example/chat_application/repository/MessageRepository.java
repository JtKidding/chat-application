package com.example.chat_application.repository;

import com.example.chat_application.entity.Group;
import com.example.chat_application.entity.Message;
import com.example.chat_application.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    // 查找兩個用戶之間的對話記錄
    @Query("SELECT m FROM Message m WHERE " +
            "(m.sender = :user1 AND m.recipient = :user2) OR " +
            "(m.sender = :user2 AND m.recipient = :user1) " +
            "ORDER BY m.timestamp ASC")
    List<Message> findConversationBetweenUsers(@Param("user1") User user1,
                                               @Param("user2") User user2);

    // 查找用戶的未讀訊息
    @Query("SELECT m FROM Message m WHERE m.recipient = :user AND m.isRead = false " +
            "ORDER BY m.timestamp DESC")
    List<Message> findUnreadMessages(@Param("user") User user);

    /*
    // 查找用戶的聊天夥伴（曾經聊過天的用戶）
    @Query("SELECT DISTINCT CASE " +
            "WHEN m.sender = :user THEN m.recipient " +
            "ELSE m.sender END " +
            "FROM Message m WHERE m.sender = :user OR m.recipient = :user")
    List<User> findChatPartners(@Param("user") User user);
    */

    // 修復：分離查詢聊天夥伴 - 查找用戶發送訊息的接收者
    @Query("SELECT DISTINCT m.recipient FROM Message m WHERE m.sender = :user")
    List<User> findRecipientsFromSender(@Param("user") User user);

    // 修復：分離查詢聊天夥伴 - 查找給用戶發送訊息的發送者
    @Query("SELECT DISTINCT m.sender FROM Message m WHERE m.recipient = :user")
    List<User> findSendersToRecipient(@Param("user") User user);

    // 查找用戶發送的所有訊息
    @Query("SELECT m FROM Message m WHERE m.sender = :user ORDER BY m.timestamp DESC")
    List<Message> findBySender(@Param("user") User user);

    // 查找用戶接收的所有訊息
    @Query("SELECT m FROM Message m WHERE m.recipient = :user ORDER BY m.timestamp DESC")
    List<Message> findByRecipient(@Param("user") User user);

    // 計算兩個用戶之間的未讀訊息數量
    @Query("SELECT COUNT(m) FROM Message m WHERE m.sender = :sender AND m.recipient = :recipient AND m.isRead = false")
    long countUnreadMessagesBetweenUsers(@Param("sender") User sender, @Param("recipient") User recipient);

    // 查找最近的訊息（用於顯示聊天列表預覽）
    @Query("SELECT m FROM Message m WHERE " +
            "(m.sender = :user1 AND m.recipient = :user2) OR " +
            "(m.sender = :user2 AND m.recipient = :user1) " +
            "ORDER BY m.timestamp DESC")
    List<Message> findLatestMessagesBetweenUsers(@Param("user1") User user1,
                                                 @Param("user2") User user2);

    // 查找群組的所有訊息
    @Query("SELECT m FROM Message m WHERE m.group = :group ORDER BY m.timestamp ASC")
    List<Message> findByGroupOrderByTimestamp(@Param("group") Group group);

    // 查找群組的最新訊息
    @Query("SELECT m FROM Message m WHERE m.group = :group ORDER BY m.timestamp DESC")
    List<Message> findByGroupOrderByTimestampDesc(@Param("group") Group group);

    // 查找用戶在群組中的未讀訊息數量
    @Query("SELECT COUNT(m) FROM Message m WHERE m.group = :group AND m.sender != :user AND m.timestamp > " +
            "(SELECT COALESCE(MAX(rm.timestamp), '1970-01-01') FROM Message rm WHERE rm.group = :group AND rm.sender = :user)")
    long countUnreadGroupMessages(@Param("group") Group group, @Param("user") User user);

    // 查找所有群組訊息（包括系統訊息）
    @Query("SELECT m FROM Message m WHERE m.group IS NOT NULL")
    List<Message> findAllGroupMessages();

    // 查找所有私人訊息
    @Query("SELECT m FROM Message m WHERE m.recipient IS NOT NULL")
    List<Message> findAllPrivateMessages();
}
