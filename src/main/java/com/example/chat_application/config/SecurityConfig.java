package com.example.chat_application.config;

import com.example.chat_application.service.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authz -> authz
                        // 允許訪問的公開路徑
                        .requestMatchers("/", "/login", "/register", "/css/**", "/js/**",
                                "/images/**", "/uploads/**", "/h2-console/**", "/group/*/preview").permitAll()
                        // 其他所有請求都需要認證
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")                                            // 自定義登入頁面
                        .defaultSuccessUrl("/home", true)       // 登入成功後跳轉
                        .failureUrl("/login?error=true")             // 登入失敗跳轉
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")      // 登出成功後跳轉
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                .csrf(csrf -> csrf
                        // 禁用CSRF保護的路徑（H2控制台和WebSocket）
                        .ignoringRequestMatchers("/h2-console/**", "/ws/**", "/api/**")
                )
                .headers(headers -> headers
                        .frameOptions().disable()                                       // 允許H2控制台使用iframe
                )
                .userDetailsService(userDetailsService);                                // 設定自定義用戶詳

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authBuilder =
                http.getSharedObject(AuthenticationManagerBuilder.class);
        authBuilder
                .userDetailsService(userDetailsService)
                .passwordEncoder(passwordEncoder());
        return authBuilder.build();
    }
}