package com.example.chat_application.config;

import com.example.chat_application.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Autowired
    private UserService userService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        String username = authentication.getName();

        try {
            // 用戶登入成功後立即設置為線上狀態
            userService.setUserOnline(username, true);
            System.out.println("用戶 " + username + " 登入成功，已設置為線上狀態");
        } catch (Exception e) {
            System.err.println("設置用戶線上狀態失敗: " + e.getMessage());
        }

        // 重定向到首頁
        response.sendRedirect("/home");
    }
}