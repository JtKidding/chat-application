package com.example.chat_application.config;

import com.example.chat_application.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketEventListener {

    @Autowired
    private UserService userService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // 記錄用戶的 WebSocket 會話
    private final Map<String, String> userSessions = new ConcurrentHashMap<>();

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = headerAccessor.getUser();
        String sessionId = headerAccessor.getSessionId();

        if (user != null) {
            String username = user.getName();
            System.out.println("WebSocket 連接建立: 用戶=" + username + ", SessionID=" + sessionId);

            // 記錄會話
            userSessions.put(sessionId, username);

            // 設置用戶為線上狀態
            try {
                userService.setUserOnline(username, true);
                System.out.println("用戶 " + username + " 已設置為線上狀態");

                // 廣播用戶上線狀態
                messagingTemplate.convertAndSend("/topic/public",
                        Map.of(
                                "type", "USER_ONLINE",
                                "username", username,
                                "message", username + " 已上線"
                        )
                );
            } catch (Exception e) {
                System.err.println("設置用戶線上狀態失敗: " + e.getMessage());
            }
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        // 從會話記錄中獲取用戶名
        String username = userSessions.remove(sessionId);

        if (username != null) {
            System.out.println("WebSocket 連接斷開: 用戶=" + username + ", SessionID=" + sessionId);

            try {
                // 檢查用戶是否還有其他活躍會話
                boolean hasOtherSessions = userSessions.containsValue(username);

                if (!hasOtherSessions) {
                    // 設置用戶為離線狀態
                    userService.setUserOnline(username, false);
                    System.out.println("用戶 " + username + " 已設置為離線狀態");

                    // 廣播用戶離線狀態
                    messagingTemplate.convertAndSend("/topic/public",
                            Map.of(
                                    "type", "USER_OFFLINE",
                                    "username", username,
                                    "message", username + " 已離線"
                            )
                    );
                } else {
                    System.out.println("用戶 " + username + " 還有其他活躍會話，保持線上狀態");
                }
            } catch (Exception e) {
                System.err.println("設置用戶離線狀態失敗: " + e.getMessage());
            }
        }
    }

    // 獲取當前線上用戶數
    public int getOnlineUserCount() {
        return (int) userSessions.values().stream().distinct().count();
    }

    // 獲取指定用戶的會話數
    public long getUserSessionCount(String username) {
        return userSessions.values().stream()
                .filter(u -> u.equals(username))
                .count();
    }

    // 手動清理指定用戶的所有會話（用於管理）
    public void clearUserSessions(String username) {
        userSessions.entrySet().removeIf(entry -> entry.getValue().equals(username));
        try {
            userService.setUserOnline(username, false);
            System.out.println("已手動清理用戶 " + username + " 的所有會話");
        } catch (Exception e) {
            System.err.println("手動清理用戶會話失敗: " + e.getMessage());
        }
    }
}