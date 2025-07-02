package com.example.chat_application.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 啟用簡單的訊息代理，用於向客戶端發送訊息
        // /topic - 用於廣播訊息（群組聊天）
        // /queue - 用於點對點訊息（私人聊天）
        config.enableSimpleBroker("/topic", "/queue");
        // 設定應用程式目標前綴
        config.setApplicationDestinationPrefixes("/app");
        // 設定用戶目標前綴
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 註冊STOMP端點，並啟用SockJS支援
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")  // 允許所有來源（開發環境）
                .withSockJS();                  // 啟用SockJS回退選項
    }
}
