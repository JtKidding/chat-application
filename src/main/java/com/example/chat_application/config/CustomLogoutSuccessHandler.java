package com.example.chat_application.config;

import com.example.chat_application.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomLogoutSuccessHandler implements LogoutSuccessHandler {

    @Autowired
    private UserService userService;

    @Override
    public void onLogoutSuccess(HttpServletRequest request,
                                HttpServletResponse response,
                                Authentication authentication) throws IOException, ServletException {

        if (authentication != null) {
            String username = authentication.getName();

            try {
                // 用戶登出時設置為離線狀態
                userService.setUserOnline(username, false);
                System.out.println("用戶 " + username + " 登出，已設置為離線狀態");
            } catch (Exception e) {
                System.err.println("設置用戶離線狀態失敗: " + e.getMessage());
            }
        }

        // 重定向到登入頁面
        response.sendRedirect("/login?logout");
    }
}