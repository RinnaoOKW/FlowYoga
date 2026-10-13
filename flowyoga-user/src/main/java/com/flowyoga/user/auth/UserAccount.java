package com.flowyoga.user.auth;

import com.flowyoga.security.AuthRole;

public record UserAccount(long id, String loginName, String passwordHash, String displayName,
                          String phone, AuthRole role, String status) {}
