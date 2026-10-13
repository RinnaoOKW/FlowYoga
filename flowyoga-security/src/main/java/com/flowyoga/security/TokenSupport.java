package com.flowyoga.security;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

public final class TokenSupport {
    public static final String COOKIE_NAME = "FLOWYOGA_AUTH";
    public static final String ISSUER = "flowyoga-user";
    public static final String AUDIENCE = "flowyoga-web";
    private TokenSupport() {}

    public static SecretKey key(String encoded) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length < 32) { throw new IllegalArgumentException("key too short"); }
            return new SecretKeySpec(bytes, "HmacSHA256");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT 密钥需要至少 32 字节的 Base64 值。请先运行 scripts/start-stage2.ps1 生成本地配置。", exception);
        }
    }

    public static OAuth2TokenValidator<Jwt> validators(OAuth2TokenValidator<Jwt> additional) {
        var validators = new ArrayList<OAuth2TokenValidator<Jwt>>();
        validators.add(new JwtTimestampValidator(Duration.ofSeconds(15)));
        validators.add(new JwtIssuerValidator(ISSUER));
        validators.add(jwt -> jwt.getExpiresAt() != null && jwt.getSubject() != null
                && jwt.getAudience() != null && jwt.getAudience().contains(AUDIENCE)
                ? OAuth2TokenValidatorResult.success() : invalid("wrong audience"));
        if (additional != null) { validators.add(additional); }
        return new DelegatingOAuth2TokenValidator<>(validators);
    }

    public static NimbusJwtDecoder decoder(String encoded, OAuth2TokenValidator<Jwt> additional) {
        var decoder = NimbusJwtDecoder.withSecretKey(key(encoded)).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(validators(additional));
        return decoder;
    }

    public static JwtAuthenticationConverter authenticationConverter() {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    public static OAuth2TokenValidatorResult invalid(String reason) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", reason, null));
    }
}
