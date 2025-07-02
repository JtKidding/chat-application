package com.example.chat_application.service;

import com.example.chat_application.entity.User;
import com.example.chat_application.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // 頭貼存儲路徑
    private final String AVATAR_UPLOAD_DIR = "uploads/avatars/";

    public User registerUser(String username, String password, String displayName) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("用戶名已存在");
        }

        User user = new User(username, passwordEncoder.encode(password), displayName);
        return userRepository.save(user);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> findById(Long userId) {
        return userRepository.findById(userId);
    }

    public List<User> findOnlineUsers() {
        return userRepository.findOnlineUsers();
    }

    public List<User> searchUsers(String search) {
        return userRepository.searchUsers(search);
    }

    public void setUserOnline(String username, boolean online) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setOnline(online);
            user.setLastSeen(LocalDateTime.now());
            userRepository.save(user);
        }
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    // 更新個人資料
    public User updateProfile(String username, String displayName, String email,
                              String phoneNumber, String bio) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用戶不存在"));

        user.setDisplayName(displayName);
        user.setEmail(email);
        user.setPhoneNumber(phoneNumber);
        user.setBio(bio);

        return userRepository.save(user);
    }

    // 更改密碼
    public void changePassword(String username, String oldPassword, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用戶不存在"));

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new RuntimeException("舊密碼不正確");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    // 上傳頭像
    public String uploadAvatar(String username, MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new RuntimeException("檔案不能為空");
        }

        // 檢查檔案類型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new RuntimeException("只能上傳圖片檔案");
        }

        // 創建上傳目錄
        Path uploadPath = Paths.get(AVATAR_UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // 生成唯一檔案名
        String originalFilename = file.getOriginalFilename();
        String fileExtension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String fileName = username + "_" + UUID.randomUUID().toString() + fileExtension;

        // 儲存檔案
        Path filePath = uploadPath.resolve(fileName);
        Files.copy(file.getInputStream(), filePath);

        // 更新用戶頭像URL
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用戶不存在"));

        // 刪除舊頭像（如果存在）
        if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
            try {
                Path oldAvatarPath = Paths.get(user.getAvatarUrl());
                Files.deleteIfExists(oldAvatarPath);
            } catch (IOException e) {
                // 記錄錯誤但不中斷流程
                System.err.println("無法刪除舊頭像: " + e.getMessage());
            }
        }

        String avatarUrl = AVATAR_UPLOAD_DIR + fileName;
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);

        return avatarUrl;
    }

    // 刪除頭像
    public void deleteAvatar(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用戶不存在"));

        if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
            try {
                Path avatarPath = Paths.get(user.getAvatarUrl());
                Files.deleteIfExists(avatarPath);
            } catch (IOException e) {
                System.err.println("無法刪除頭像檔案: " + e.getMessage());
            }

            user.setAvatarUrl(null);
            userRepository.save(user);
        }
    }

    // 取得頭像URL（用於顯示）
    public String getAvatarUrl(String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user != null && user.getAvatarUrl() != null) {
            return "/uploads/avatars/" + Paths.get(user.getAvatarUrl()).getFileName().toString();
        }
        return "/images/default-avatar.png"; // 預設頭像
    }
}
