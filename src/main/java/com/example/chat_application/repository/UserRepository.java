package com.example.chat_application.repository;

import com.example.chat_application.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // 根據用戶名查找用戶
    Optional<User> findByUsername(String username);

    // 查找線上用戶
    @Query("SELECT u FROM User u WHERE u.online = true")
    List<User> findOnlineUsers();

    // 搜尋用戶（根據用戶名或顯示名稱）
    @Query("SELECT u FROM User u WHERE u.username LIKE %:search% OR u.displayName LIKE %:search%")
    List<User> searchUsers(@Param("search") String search);

    // 根據電子郵件查找用戶
    Optional<User> findByEmail(String email);

    // 查找所有用戶，按最後上線時間排序
    @Query("SELECT u FROM User u ORDER BY u.lastSeen DESC")
    List<User> findAllOrderByLastSeen();
}
