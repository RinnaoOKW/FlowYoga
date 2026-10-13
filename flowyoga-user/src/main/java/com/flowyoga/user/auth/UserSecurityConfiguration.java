package com.flowyoga.user.auth;

import com.flowyoga.security.TokenSupport;
import com.flowyoga.management.BrowserCsrf;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class UserSecurityConfiguration {
    @Bean Clock authClock() { return Clock.systemUTC(); }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(10); }
    @Bean CookieCsrfTokenRepository csrfTokenRepository() { return CookieCsrfTokenRepository.withHttpOnlyFalse(); }

    @Bean
    JwtDecoder jwtDecoder(@Value("${flowyoga.security.jwt-secret}") String secret, AuthMapper mapper, Clock clock) {
        return TokenSupport.decoder(secret, jwt -> {
            try {
                long userId = Long.parseLong(jwt.getSubject());
                String role = jwt.getClaimAsString("role");
                if (jwt.getId() == null || role == null || mapper.activeSession(jwt.getId(), userId, role,
                        LocalDateTime.now(clock)) != 1) {
                    return TokenSupport.invalid("session is inactive");
                }
                return org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success();
            } catch (NumberFormatException exception) {
                return TokenSupport.invalid("invalid subject");
            }
        });
    }

    @Bean
    SecurityFilterChain userSecurity(HttpSecurity http, JwtDecoder decoder, CookieCsrfTokenRepository csrf) throws Exception {
        return http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .addFilterBefore(BrowserCsrf.filter(csrf),BearerTokenAuthenticationFilter.class)
                .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/internal/accounts/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/user/status", "/actuator/health/**", "/api/user/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/user/auth/login").permitAll()
                        .requestMatchers("/api/user/workspaces/admin").hasRole("ADMIN")
                        .requestMatchers("/api/user/workspaces/coach").hasRole("COACH")
                        .requestMatchers("/api/user/workspaces/member").hasRole("MEMBER")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resource -> resource.bearerTokenResolver(UserSecurityConfiguration::bearerToken)
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(TokenSupport.authenticationConverter()))
                        .authenticationEntryPoint((request, response, error) -> jsonError(response, 401, "UNAUTHENTICATED", "请先登录或重新登录")))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) -> jsonError(response, 401, "UNAUTHENTICATED", "请先登录或重新登录"))
                        .accessDeniedHandler((request, response, error) -> jsonError(response, 403, "FORBIDDEN", "当前身份没有权限执行此操作")))
                .build();
    }

    private static String bearerToken(HttpServletRequest request) {
        if (request.getRequestURI().startsWith("/internal/accounts/")) return null;
        if (request.getRequestURI().equals("/api/user/auth/login") || request.getRequestURI().equals("/api/user/auth/csrf")) {
            return null; // 过期 Cookie 不应阻止重新登录与获取 CSRF 令牌。
        }
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) { return authorization.substring(7); }
        Cookie[] cookies = request.getCookies();
        return cookies == null ? null : Arrays.stream(cookies)
                .filter(cookie -> cookie.getName().equals(TokenSupport.COOKIE_NAME)).map(Cookie::getValue).findFirst().orElse(null);
    }

    private static void jsonError(jakarta.servlet.http.HttpServletResponse response, int status, String code, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
