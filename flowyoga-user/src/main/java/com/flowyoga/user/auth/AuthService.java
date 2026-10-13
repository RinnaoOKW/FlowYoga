package com.flowyoga.user.auth;

import com.flowyoga.security.AuthRole;
import com.flowyoga.security.TokenSupport;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    public static final Duration SESSION_DURATION = Duration.ofMinutes(30);
    private final AuthMapper mapper;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final JwtEncoder encoder;
    private final String dummyPasswordHash;

    public AuthService(AuthMapper mapper, PasswordEncoder passwords, Clock clock,
                       @Value("${flowyoga.security.jwt-secret}") String secret) {
        this.mapper = mapper;
        this.passwords = passwords;
        this.clock = clock;
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(TokenSupport.key(secret)));
        this.dummyPasswordHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public IssuedSession login(String loginName, String password, AuthRole requestedRole) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "密码长度超出允许范围");
        }
        UserAccount account = mapper.findByLoginName(loginName.trim());
        boolean matches = passwords.matches(password, account == null ? dummyPasswordHash : account.passwordHash());
        if (!matches || account == null || !account.status().equals("ACTIVE") || account.role() != requestedRole) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "账号、密码或所选身份不匹配，请重新检查");
        }
        Instant now = clock.instant();
        Instant expiresAt = now.plus(SESSION_DURATION);
        String sessionId = UUID.randomUUID().toString();
        mapper.createSession(sessionId, account.id(), LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC), LocalDateTime.now(clock));
        var claims = JwtClaimsSet.builder().issuer(TokenSupport.ISSUER).audience(List.of(TokenSupport.AUDIENCE))
                .subject(Long.toString(account.id())).id(sessionId).issuedAt(now).expiresAt(expiresAt)
                .claim("role", account.role().name()).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
        return new IssuedSession(token, view(account), expiresAt);
    }

    public SessionView me(Jwt jwt) {
        UserAccount account = mapper.findById(Long.parseLong(jwt.getSubject()));
        return new SessionView(view(account), jwt.getExpiresAt());
    }

    @Transactional
    public void logout(Jwt jwt) { mapper.revokeSession(jwt.getId(), LocalDateTime.now(clock)); }

    private UserView view(UserAccount account) {
        return new UserView(Long.toString(account.id()), account.loginName(), account.displayName(), account.phone(),
                account.role(), account.role().label(), account.status(), mapper.permissions(account.role().name()));
    }

    public record UserView(String id, String loginName, String displayName, String phone, AuthRole role,
                           String roleLabel, String status, List<String> permissions) {}
    public record SessionView(UserView user, Instant expiresAt) {}
    public record IssuedSession(String token, UserView user, Instant expiresAt) {}
}
