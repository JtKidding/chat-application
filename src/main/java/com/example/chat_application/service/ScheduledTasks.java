package com.example.chat_application.service;

import com.example.chat_application.entity.User;
import com.example.chat_application.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class ScheduledTasks {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    // 每5分鐘檢查一次離線用戶
    @Scheduled(fixedRate = 300000) // 300000ms = 5分鐘
    public void cleanupOfflineUsers() {
        try {
            // 查找超過2分鐘沒有活動的線上用戶
            LocalDateTime threshold = LocalDateTime.now().minusMinutes(2);

            List<User> onlineUsers = userRepository.findOnlineUsers();
            int cleanedCount = 0;

            for (User user : onlineUsers) {
                if (user.getLastSeen() != null && user.getLastSeen().isBefore(threshold)) {
                    // 設置為離線
                    userService.setUserOnline(user.getUsername(), false);
                    cleanedCount++;
                    System.out.println("自動設置用戶 " + user.getUsername() + " 為離線狀態（超時）");
                }
            }

            if (cleanedCount > 0) {
                System.out.println("定時清理完成，設置 " + cleanedCount + " 個用戶為離線狀態");
            }

        } catch (Exception e) {
            System.err.println("定時清理離線用戶時發生錯誤: " + e.getMessage());
        }
    }

    // 每小時清理一次邀請（可選）
    @Scheduled(fixedRate = 3600000) // 3600000ms = 1小時
    public void cleanupExpiredInvitations() {
        try {
            // 這裡可以添加清理過期邀請的邏輯
            System.out.println("執行邀請清理任務...");
        } catch (Exception e) {
            System.err.println("清理過期邀請時發生錯誤: " + e.getMessage());
        }
    }
}