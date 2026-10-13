package com.flowyoga.user.management;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;
@Mapper
public interface AccountChangeMapper {
    String FILTER="""
        role_code=#{role} AND (creation_request_id IS NULL OR EXISTS
          (SELECT 1 FROM account_change c WHERE c.request_id=sys_user.creation_request_id AND c.state='APPLIED'))
        """;
    String SEARCH="""
        AND (#{status}='' OR status=#{status})
        AND (#{keyword}='' OR display_name LIKE CONCAT('%',#{keyword},'%') ESCAPE '!'
            OR phone LIKE CONCAT('%',#{keyword},'%') ESCAPE '!'
            OR login_name LIKE CONCAT('%',#{keyword},'%') ESCAPE '!')
        """;
    @Select("SELECT id,login_name,password_hash,display_name,phone,role_code AS role,status FROM sys_user WHERE "+FILTER+SEARCH+" ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
    List<com.flowyoga.user.auth.UserAccount> search(@Param("role") String role,@Param("keyword") String keyword,@Param("status") String status,@Param("size") int size,@Param("offset") int offset);
    @Select("SELECT COUNT(*) FROM sys_user WHERE "+FILTER+SEARCH) long count(@Param("role") String role,@Param("keyword") String keyword,@Param("status") String status);
    @Select("SELECT COUNT(*) FROM sys_user WHERE "+FILTER+" AND (#{status}='' OR status=#{status})") long total(@Param("role") String role,@Param("status") String status);
    @Select("SELECT * FROM account_change WHERE request_id=#{id} FOR UPDATE")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    Change find(String id);
    @Insert("INSERT INTO account_change (request_id,role_code,operation,state,created_at,updated_at) VALUES (#{id},#{role},#{operation},#{state},#{now},#{now})")
    int insertChange(@Param("id") String id,@Param("role") String role,@Param("operation") String operation,@Param("state") String state,@Param("now") LocalDateTime now);
    @Update("UPDATE account_change SET user_id=#{userId},payload_hash=#{hash},payload_json=#{json},updated_at=#{now} WHERE request_id=#{id}")
    int prepare(@Param("id") String id,@Param("userId") long userId,@Param("hash") String hash,@Param("json") String json,@Param("now") LocalDateTime now);
    @Update("UPDATE account_change SET state=#{state},updated_at=#{now} WHERE request_id=#{id}") int state(@Param("id") String id,@Param("state") String state,@Param("now") LocalDateTime now);
    @Select("SELECT id FROM sys_user WHERE id=#{id} FOR UPDATE") Long lockUser(long id);
    @Insert("INSERT INTO sys_user (login_name,password_hash,display_name,phone,role_code,creation_request_id,status,created_at,updated_at) VALUES (#{loginName},#{passwordHash},#{displayName},#{phone},#{role},#{requestId},'DISABLED',#{now},#{now})")
    @Options(useGeneratedKeys=true,keyProperty="id") int create(NewAccount account);
    @Update("UPDATE sys_user SET display_name=COALESCE(#{name},display_name),phone=COALESCE(#{phone},phone),status=COALESCE(#{status},status),updated_at=#{now} WHERE id=#{id}")
    int update(@Param("id") long id,@Param("name") String name,@Param("phone") String phone,@Param("status") String status,@Param("now") LocalDateTime now);
    @Update("UPDATE auth_session SET revoked_at=#{now} WHERE user_id=#{id} AND revoked_at IS NULL") int revoke(@Param("id") long id,@Param("now") LocalDateTime now);
    public record Change(String requestId,String roleCode,String operation,String state,Long userId,String payloadHash,String payloadJson,LocalDateTime createdAt,LocalDateTime updatedAt) {}
    public static class NewAccount { public long id; public String loginName,passwordHash,displayName,phone,role,requestId; public LocalDateTime now; }
}
