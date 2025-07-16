package com.example.chat_application.repository;

import com.example.chat_application.entity.Group;
import com.example.chat_application.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

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

    // 查找群組並立即加載成員
    @Query("SELECT g FROM Group g LEFT JOIN FETCH g.members WHERE g.id = :id")
    Optional<Group> findByIdWithMembers(@Param("id") Long id);

    // 查找用戶的群組並立即加載成員
    @Query("SELECT DISTINCT g FROM Group g LEFT JOIN FETCH g.members m WHERE m = :user")
    List<Group> findByMembersContainingWithMembers(@Param("user") User user);

    // 檢查用戶是否為群組成員（避免懶加載）
    @Query("SELECT COUNT(g) > 0 FROM Group g JOIN g.members m WHERE g.id = :groupId AND m.id = :userId")
    boolean isUserMemberOfGroupById(@Param("groupId") Long groupId, @Param("userId") Long userId);

    // 保留原有的方法，但也加入基於 ID 的版本
    @Query("SELECT COUNT(g) > 0 FROM Group g JOIN g.members m WHERE g.id = :groupId AND m = :user")
    boolean isUserMemberOfGroup(@Param("groupId") Long groupId, @Param("user") User user);

    // 新增：根據 ID 和用戶名檢查成員資格
    @Query("SELECT COUNT(g) > 0 FROM Group g JOIN g.members m WHERE g.id = :groupId AND m.username = :username")
    boolean isUserMemberOfGroupByUsername(@Param("groupId") Long groupId, @Param("username") String username);

    // 查找群組的成員數量
    @Query("SELECT SIZE(g.members) FROM Group g WHERE g.id = :groupId")
    int getMemberCount(@Param("groupId") Long groupId);

    // 查找公開群組並立即加載成員
    @Query("SELECT g FROM Group g LEFT JOIN FETCH g.members WHERE g.isPrivate = false ORDER BY g.createdAt DESC")
    List<Group> findPublicGroupsWithMembers();
}
