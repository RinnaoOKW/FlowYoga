package com.flowyoga.security;

public enum AuthRole {
    ADMIN("管理员"), COACH("教练"), MEMBER("会员");

    private final String label;
    AuthRole(String label) { this.label = label; }
    public String label() { return label; }
}
