package com.flowyoga.user.auth;

import com.flowyoga.security.AuthRole;
import com.flowyoga.security.TokenSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
    private final AuthService auth;
    private final CookieCsrfTokenRepository csrf;
    private final boolean secureCookie;

    public AuthController(AuthService auth, CookieCsrfTokenRepository csrf,
                          @Value("${flowyoga.security.secure-cookie:false}") boolean secureCookie) {
        this.auth = auth;
        this.csrf = csrf;
        this.secureCookie = secureCookie;
    }

    @GetMapping("/api/user/auth/csrf")
    public CsrfView csrf(CsrfToken token) { return new CsrfView(token.getHeaderName(), token.getToken()); }

    @PostMapping("/api/user/auth/login")
    public AuthService.SessionView login(@Valid @RequestBody LoginRequest input,
                                        HttpServletRequest request, HttpServletResponse response) {
        var session = auth.login(input.loginName(), input.password(), input.role());
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(TokenSupport.COOKIE_NAME, session.token())
                .httpOnly(true).secure(secureCookie).sameSite("Lax").path("/").maxAge(AuthService.SESSION_DURATION).build().toString());
        csrf.saveToken(null, request, response); // 登录后重新获取 CSRF 令牌。
        return new AuthService.SessionView(session.user(), session.expiresAt());
    }

    @GetMapping("/api/user/auth/me")
    public AuthService.SessionView me(@AuthenticationPrincipal Jwt jwt) { return auth.me(jwt); }

    @PostMapping("/api/user/auth/logout")
    public void logout(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request, HttpServletResponse response) {
        auth.logout(jwt);
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(TokenSupport.COOKIE_NAME, "")
                .httpOnly(true).secure(secureCookie).sameSite("Lax").path("/").maxAge(0).build().toString());
        csrf.saveToken(null, request, response);
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    @GetMapping("/api/user/workspaces/{role}")
    public AuthService.SessionView workspace(@PathVariable String role, @AuthenticationPrincipal Jwt jwt) {
        var session = auth.me(jwt);
        if (!session.user().role().name().toLowerCase(Locale.ROOT).equals(role)) {
            throw new AuthException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前身份不能进入该空间");
        }
        return session;
    }

    public record LoginRequest(@NotBlank(message = "请输入账号") @Size(max = 50) String loginName,
                               @NotBlank(message = "请输入密码") @Size(max = 72) String password,
                               @NotNull(message = "请选择登录身份") AuthRole role) {}
    public record CsrfView(String headerName, String token) {}
}
