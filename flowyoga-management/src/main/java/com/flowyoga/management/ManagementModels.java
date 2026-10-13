package com.flowyoga.management;

import com.flowyoga.security.AuthRole;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class ManagementModels {
    private ManagementModels() {}
    public enum Status { ACTIVE, DISABLED }
    public record CreatePerson(@NotNull UUID requestId,
        @NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9_.-]{2,49}", message="登录账号需为 3–50 位字母、数字、下划线、点或短横线") String loginName,
        @NotBlank @Size(min=8,max=72,message="初始密码需为 8–72 位") String initialPassword,
        @NotBlank @Size(max=50) String displayName,
        @NotBlank @Pattern(regexp="1[3-9][0-9]{9}",message="请填写有效的 11 位手机号") String phone,
        @Size(max=255) String specialty, @Size(max=1000) String introduction,
        @NotNull Status status) {}
    public record EditPerson(@NotNull UUID requestId, @Min(0) int version,
        @NotBlank @Size(max=50) String displayName,
        @NotBlank @Pattern(regexp="1[3-9][0-9]{9}",message="请填写有效的 11 位手机号") String phone,
        @Size(max=255) String specialty, @Size(max=1000) String introduction) {}
    public record SetStatus(@NotNull UUID requestId, @Min(0) int version, @NotNull Status status) {}
    // 不包含初始密码；可安全持久保存，用于进程重启后的恢复。
    public record ChangePayload(String loginName,String displayName,String phone,String specialty,String introduction,Status status) {}
    public record AccountView(String id,String loginName,String displayName,String phone,AuthRole role,Status status) {}
    public record Statistics(long total,long active,long disabled) {}
    public record AccountPage(List<AccountView> items,long total,Statistics statistics) {}
    public record ChangeReceipt(String requestId,String state,AccountView account) {}
    public record UpdateAccount(UUID requestId,@Size(max=50) String displayName,
        @Pattern(regexp="1[3-9][0-9]{9}") String phone,Status status) {}
    public record SessionQuery(@NotBlank @Size(max=36) String sessionId,@Positive long userId,@NotNull AuthRole role) {}
    public record SessionResult(boolean active) {}
    public record Profile(long id,long userId,String status,String specialty,String introduction,int version,LocalDateTime createdAt) {}
    public record PersonView(String id,String userId,String loginName,String displayName,String phone,
        String status,Status accountStatus,String specialty,String introduction,int version,String syncState,LocalDateTime createdAt) {}
    public record Task(String requestId,String operation,String state,Long profileId,Long userId,String loginName,
        String payloadJson,String payloadHash,int expectedVersion,int retryCount,String lastError,LocalDateTime updatedAt) {}
    public record TaskView(String requestId,String operation,String state,String loginName,int retryCount,String lastError,LocalDateTime updatedAt) {}
    public record PersonPage(List<PersonView> items,long total,int page,int size,Statistics statistics,List<TaskView> pendingChanges) {}
    public record OperationResult(String requestId,String state,String personId,String message) {}
    public record ApiError(String code,String message) {}
}
