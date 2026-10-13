package com.flowyoga.user.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flowyoga.security.AuthRole;
import com.flowyoga.security.TokenSupport;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:flowyoga_auth;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.cloud.nacos.discovery.enabled=false", "spring.cloud.discovery.enabled=false",
        "flowyoga.security.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=", "flowyoga.demo.enabled=true",
        "flowyoga.internal.account-token=test-account-internal-secret-32-bytes"
})
@AutoConfigureMockMvc
class AuthIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void resetSessions() {
        jdbc.update("DELETE FROM auth_session");
        jdbc.update("UPDATE sys_user SET status = 'ACTIVE'");
    }

    @ParameterizedTest
    @EnumSource(AuthRole.class)
    void allThreeRolesCanLoginWithTheirOwnIdentity(AuthRole role) throws Exception {
        MvcResult result = mvc.perform(post("/api/user/auth/login").with(csrf()).contentType("application/json")
                        .content(credentials(role.name().toLowerCase(Locale.ROOT), "FlowYoga@123", role.name())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value(role.name()))
                .andExpect(jsonPath("$.user.permissions").isNotEmpty()).andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist()).andReturn();
        String cookie = authHeader(result);
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Lax"));
        mvc.perform(get("/api/user/auth/me").cookie(cookie(result))).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value(role.name()));
        mvc.perform(get("/api/user/workspaces/" + role.name().toLowerCase(Locale.ROOT)).cookie(cookie(result)))
                .andExpect(status().isOk());
    }

    @Test void anonymousRequestsCannotReadAccount() throws Exception {
        mvc.perform(get("/api/user/auth/me")).andExpect(status().isUnauthorized());
    }
    @Test void validPasswordCannotSelectAnotherRole() throws Exception {
        mvc.perform(post("/api/user/auth/login").with(csrf()).contentType("application/json")
                .content(credentials("member", "FlowYoga@123", "ADMIN")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM auth_session", Integer.class));
    }
    @Test void wrongPasswordDoesNotCreateSession() throws Exception {
        mvc.perform(post("/api/user/auth/login").with(csrf()).contentType("application/json")
                .content(credentials("admin", "wrong", "ADMIN"))).andExpect(status().isUnauthorized());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM auth_session", Integer.class));
    }
    @Test void loginRequiresCsrfProtection() throws Exception {
        mvc.perform(post("/api/user/auth/login").contentType("application/json")
                .content(credentials("admin", "FlowYoga@123", "ADMIN"))).andExpect(status().isForbidden());
    }
    @Test void memberCannotAccessAdminOrCoachWorkspace() throws Exception {
        Cookie cookie = cookie(login("member", "MEMBER"));
        mvc.perform(get("/api/user/workspaces/admin").cookie(cookie)).andExpect(status().isForbidden());
        mvc.perform(get("/api/user/workspaces/coach").cookie(cookie)).andExpect(status().isForbidden());
    }
    @Test void forgedBrowserRoleHeadersDoNotAuthenticate() throws Exception {
        mvc.perform(get("/api/user/workspaces/admin").header("X-User-Id", "1001").header("X-Role", "ADMIN"))
                .andExpect(status().isUnauthorized());
    }
    @Test void logoutRevokesTheOriginalToken() throws Exception {
        Cookie original = cookie(login("coach", "COACH"));
        mvc.perform(post("/api/user/auth/logout").cookie(original).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/user/auth/me").cookie(original)).andExpect(status().isUnauthorized());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM auth_session WHERE revoked_at IS NOT NULL", Integer.class));
    }
    @Test void authenticatedCookieLogoutStillRequiresCsrf() throws Exception {
        Cookie original = cookie(login("member", "MEMBER"));
        mvc.perform(post("/api/user/auth/logout").cookie(original)).andExpect(status().isForbidden());
        mvc.perform(get("/api/user/auth/me").cookie(original)).andExpect(status().isOk());
    }
    @Test void disabledUserCannotReuseExistingSession() throws Exception {
        Cookie cookie = cookie(login("member", "MEMBER"));
        jdbc.update("UPDATE sys_user SET status = 'DISABLED' WHERE login_name = 'member'");
        mvc.perform(get("/api/user/auth/me").cookie(cookie)).andExpect(status().isUnauthorized());
    }
    @Test void expiredDatabaseSessionIsRejected() throws Exception {
        Cookie cookie = cookie(login("admin", "ADMIN"));
        jdbc.update("UPDATE auth_session SET created_at = ?, expires_at = ?", LocalDateTime.now(java.time.Clock.systemUTC()).minusHours(2), LocalDateTime.now(java.time.Clock.systemUTC()).minusHours(1));
        mvc.perform(get("/api/user/auth/me").cookie(cookie)).andExpect(status().isUnauthorized());
    }
    @Test void tamperedSignatureIsRejected() throws Exception {
        Cookie original = cookie(login("member", "MEMBER"));
        String token = original.getValue();
        int signature = token.lastIndexOf('.') + 1;
        char replacement = token.charAt(signature) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, signature) + replacement + token.substring(signature + 1);
        mvc.perform(get("/api/user/auth/me").cookie(new Cookie(TokenSupport.COOKIE_NAME, tampered))).andExpect(status().isUnauthorized());
    }
    @Test void staleCookieDoesNotPreventLoginOrCsrfRefresh() throws Exception {
        Cookie stale = new Cookie(TokenSupport.COOKIE_NAME, "invalid-old-token");
        mvc.perform(get("/api/user/auth/csrf").cookie(stale)).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
        mvc.perform(post("/api/user/auth/login").cookie(stale).with(csrf()).contentType("application/json")
                .content(credentials("admin", "FlowYoga@123", "ADMIN"))).andExpect(status().isOk());
    }
    @Test void sqlInjectionInputCannotBypassPasswordCheck() throws Exception {
        mvc.perform(post("/api/user/auth/login").with(csrf()).contentType("application/json")
                .content(credentials("' OR '1'='1", "anything", "ADMIN"))).andExpect(status().isUnauthorized());
    }
    @Test void passwordsAreStoredAsBcryptHashes() {
        String hash = jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE login_name = 'admin'", String.class);
        assertNotEquals("FlowYoga@123", hash);
        assertTrue(hash.startsWith("$2"));
    }
    @Test void unsupportedRoleHasReadableValidationError() throws Exception {
        mvc.perform(post("/api/user/auth/login").with(csrf()).contentType("application/json")
                .content(credentials("admin", "FlowYoga@123", "SUPERUSER")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private MvcResult login(String name, String role) throws Exception {
        return mvc.perform(post("/api/user/auth/login").with(csrf()).contentType("application/json")
                .content(credentials(name, "FlowYoga@123", role))).andExpect(status().isOk()).andReturn();
    }
    private static String credentials(String name, String password, String role) {
        return "{\"loginName\":\"" + name + "\",\"password\":\"" + password + "\",\"role\":\"" + role + "\"}";
    }
    private static String authHeader(MvcResult result) {
        return result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(header -> header.startsWith(TokenSupport.COOKIE_NAME + "=")).findFirst().orElseThrow();
    }
    private static Cookie cookie(MvcResult result) {
        String value = authHeader(result).split(";", 2)[0].substring(TokenSupport.COOKIE_NAME.length() + 1);
        return new Cookie(TokenSupport.COOKIE_NAME, value);
    }
}
