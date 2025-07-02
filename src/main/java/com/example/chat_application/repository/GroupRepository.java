package com.example.chat_application.repository;

import com.example.chat_application.entity.Group;
import com.example.chat_application.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {

    // 查找用戶創建的群組
    List<Group> findByCreator(User creator);

    // 查找用戶參與的所有群組
    @Query("SELECT g FROM Group g JOIN g.members m WHERE m = :user")
    List<Group> findByMembersContaining(@Param("user") User user);

    // 搜尋公開群組
    @Query("SELECT g FROM Group g WHERE g.isPrivate = false AND " +
            "(g.name LIKE %:search% OR g.description LIKE %:search%)")
    List<Group> searchPublicGroups(@Param("search") String search);

    // 查找所有公開群組
    @Query("SELECT g FROM Group g WHERE g.isPrivate = false ORDER BY g.createdAt DESC")
    List<Group> findPublicGroups();

    // 根據名稱查找群組
    List<Group> findByNameContainingIgnoreCase(String name);

    // 查找用戶不在其中的公開群組
    @Query("SELECT g FROM Group g WHERE g.isPrivate = false AND :user NOT MEMBER OF g.members")
    List<Group> findPublicGroupsNotJoinedByUser(@Param("user") User user);
}
