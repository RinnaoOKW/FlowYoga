package com.flowyoga.management;
import com.flowyoga.security.AuthRole;
public enum DirectoryKind {
    MEMBER(AuthRole.MEMBER,"member_profile","member_account_task","member_id"),
    COACH(AuthRole.COACH,"coach_profile","coach_account_task","coach_id");
    public final AuthRole role; public final String profileTable,taskTable,profileColumn;
    DirectoryKind(AuthRole role,String profileTable,String taskTable,String profileColumn) { this.role=role; this.profileTable=profileTable; this.taskTable=taskTable; this.profileColumn=profileColumn; }
}
