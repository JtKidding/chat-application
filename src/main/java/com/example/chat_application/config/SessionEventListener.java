package com.example.chat_application.config;

import com.example.chat_application.service.UserService;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

@Component
public class SessionEventListener implements HttpSessionListener {

    @Autowired
    private UserService userService;

    @Override
    public void sessionCreated(HttpSessionEvent se) {
        System.out.println("Session 創建: " + se.getSession().getId());
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        System.out.println("Session 銷毀: " + se.getSession().getId());

        try {
            // 嘗試從 Session 中獲取用戶信息
            SecurityContext securityContext = (SecurityContext) se.getSession()
                    .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);

            if (securityContext != null) {
                Authentication authentication = securityContext.getAuthentication();
                if (authentication != null && authentication.isAuthenticated()) {
                    String username = authentication.getName();

                    // 設置用戶為離線狀態
                    userService.setUserOnline(username, false);
                    System.out.println("Session 過期，用戶 " + username + " 已設置為離線狀態");
                }
            }
        } catch (Exception e) {
            System.err.println("處理 Session 銷毀事件時發生錯誤: " + e.getMessage());
        }
    }
}