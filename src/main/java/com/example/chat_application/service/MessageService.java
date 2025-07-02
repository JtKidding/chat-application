package com.example.chat_application.service;

import com.example.chat_application.entity.Group;
import com.example.chat_application.entity.Message;
import com.example.chat_application.entity.User;
import com.example.chat_application.repository.GroupRepository;
import com.example.chat_application.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MessageService {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private GroupRepository groupRepository;

    public Message saveMessage(User sender, User recipient, String content) {
        Message message = new Message(sender, recipient, content);
        return messageRepository.save(message);
    }

    public List<Message> getConversation(User user1, User user2) {
        return messageRepository.findConversationBetweenUsers(user1, user2);
    }

    public List<Message> getUnreadMessages(User user) {
        return messageRepository.findUnreadMessages(user);
    }

    public List<User> getChatPartners(User user) {
        try {
            Set<User> partners = new HashSet<>();

            // 獲取所有接收過我訊息的用戶
            List<User> recipients = messageRepository.findRecipientsFromSender(user);
            if (recipients != null) {
                partners.addAll(recipients);
            }

            // 獲取所有給我發送過訊息的用戶
            List<User> senders = messageRepository.findSendersToRecipient(user);
            if (senders != null) {
                partners.addAll(senders);
            }

            return new ArrayList<>(partners);

        } catch (Exception e) {
            System.err.println("獲取聊天夥伴時發生錯誤: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public void markAsRead(Long messageId) {
        messageRepository.findById(messageId).ifPresent(message -> {
            message.setRead(true);
            messageRepository.save(message);
        });
    }

    // 標記與特定用戶的所有訊息為已讀
    public void markConversationAsRead(User recipient, User sender) {
        List<Message> unreadMessages = messageRepository.findUnreadMessages(recipient);
        unreadMessages.stream()
                .filter(msg -> msg.getSender().equals(sender))
                .forEach(msg -> {
                    msg.setRead(true);
                    messageRepository.save(msg);
                });
    }

    // 計算未讀訊息數量
    public long getUnreadMessageCount(User user) {
        return messageRepository.findUnreadMessages(user).size();
    }

    // 儲存群組訊息
    public Message saveGroupMessage(User sender, Group group, String content) {
        Message message = new Message(sender, group, content);
        return messageRepository.save(message);
    }

    // 取得群組對話記錄
    public List<Message> getGroupConversation(Group group) {
        return messageRepository.findByGroupOrderByTimestamp(group);
    }

    // 取得群組的最新訊息
    public Optional<Message> getLatestGroupMessage(Group group) {
        List<Message> messages = messageRepository.findByGroupOrderByTimestampDesc(group);
        return messages.isEmpty() ? Optional.empty() : Optional.of(messages.get(0));
    }

    // 計算群組未讀訊息數（簡化版本）
    public long getGroupUnreadCount(Group group, User user) {
        try {
            return messageRepository.countUnreadGroupMessages(group, user);
        } catch (Exception e) {
            // 如果查詢失敗，返回0
            return 0;
        }
    }

    // 取得用戶所有群組的最新訊息
    public Map<Long, Message> getLatestGroupMessages(User user) {
        List<Group> userGroups = groupRepository.findByMembersContaining(user);
        Map<Long, Message> latestMessages = new HashMap<>();

        for (Group group : userGroups) {
            Optional<Message> latestMessage = getLatestGroupMessage(group);
            latestMessage.ifPresent(message -> latestMessages.put(group.getId(), message));
        }

        return latestMessages;
    }

    // 判斷訊息類型
    public boolean isPrivateMessage(Message message) {
        return message.getRecipient() != null && message.getGroup() == null;
    }

    public boolean isGroupMessage(Message message) {
        return message.getGroup() != null && message.getRecipient() == null;
    }
}