package com.flowyoga.user.auth;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AuthMapper {
    @Select("SELECT id, login_name, password_hash, display_name, phone, role_code AS role, status FROM sys_user WHERE login_name = #{loginName}")
    UserAccount findByLoginName(String loginName);

    @Select("SELECT id, login_name, password_hash, display_name, phone, role_code AS role, status FROM sys_user WHERE id = #{id}")
    UserAccount findById(long id);

    @Select("SELECT permission_code FROM auth_role_permission WHERE role_code = #{role} ORDER BY permission_code")
    List<String> permissions(String role);

    @Insert("INSERT INTO auth_session (id, user_id, expires_at, created_at) VALUES (#{id}, #{userId}, #{expiresAt}, #{now})")
    int createSession(@Param("id") String id, @Param("userId") long userId,
                      @Param("expiresAt") LocalDateTime expiresAt, @Param("now") LocalDateTime now);

    @Select("""
        SELECT COUNT(*) FROM auth_session s JOIN sys_user u ON u.id = s.user_id
        WHERE s.id = #{id} AND u.id = #{userId} AND u.role_code = #{role}
          AND u.status = 'ACTIVE' AND s.revoked_at IS NULL AND s.expires_at > #{now}
        """)
    int activeSession(@Param("id") String id, @Param("userId") long userId,
                      @Param("role") String role, @Param("now") LocalDateTime now);

    @Update("UPDATE auth_session SET revoked_at = #{now} WHERE id = #{id} AND revoked_at IS NULL")
    int revokeSession(@Param("id") String id, @Param("now") LocalDateTime now);

    @Insert("""
        INSERT INTO sys_user (id, login_name, password_hash, display_name, phone, role_code, status)
        VALUES (#{id}, #{loginName}, #{passwordHash}, #{displayName}, #{phone}, #{role}, 'ACTIVE')
        """)
    int insertDemoAccount(@Param("id") long id, @Param("loginName") String loginName,
                          @Param("passwordHash") String passwordHash, @Param("displayName") String displayName,
                          @Param("phone") String phone, @Param("role") String role);
}
