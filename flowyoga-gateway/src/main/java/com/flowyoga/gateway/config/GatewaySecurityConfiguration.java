package com.flowyoga.gateway.config;

import com.flowyoga.security.TokenSupport;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfiguration {
    @Bean
    ReactiveJwtDecoder gatewayJwtDecoder(@Value("${flowyoga.security.jwt-secret}") String secret) {
        var decoder = NimbusReactiveJwtDecoder.withSecretKey(TokenSupport.key(secret)).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(TokenSupport.validators(null));
        return decoder;
    }

    @Bean
    SecurityWebFilterChain gatewaySecurity(ServerHttpSecurity http, ReactiveJwtDecoder decoder) {
        ServerAuthenticationConverter tokens = exchange -> {
            String path = exchange.getRequest().getPath().value();
            if (path.equals("/api/user/auth/login") || path.equals("/api/user/auth/csrf")) { return Mono.empty(); }
            String header = exchange.getRequest().getHeaders().getFirst("Authorization");
            String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
            if (token == null) {
                var cookie = exchange.getRequest().getCookies().getFirst(TokenSupport.COOKIE_NAME);
                token = cookie == null ? null : cookie.getValue();
            }
            return token == null || token.isBlank() ? Mono.empty() : Mono.just(new BearerTokenAuthenticationToken(token));
        };
        return http
                // Cookie 写操作的 CSRF 校验由拥有接口的用户服务执行。
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable).formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .authorizeExchange(authorize -> authorize
                        .pathMatchers("/api/gateway/status", "/api/user/status", "/api/member/status", "/api/course/status",
                                "/actuator/health/**", "/api/course/integration/member-card", "/api/user/auth/csrf", "/api/user/auth/login").permitAll()
                        .pathMatchers("/api/user/workspaces/admin").hasRole("ADMIN")
                        .pathMatchers("/api/user/workspaces/coach").hasRole("COACH")
                        .pathMatchers("/api/user/workspaces/member").hasRole("MEMBER")
                        .pathMatchers("/api/member/admin/**", "/api/course/admin/**").hasRole("ADMIN")
                        .anyExchange().authenticated())
                .oauth2ResourceServer(resource -> resource.bearerTokenConverter(tokens)
                        .jwt(jwt -> jwt.jwtDecoder(decoder).jwtAuthenticationConverter(
                                new ReactiveJwtAuthenticationConverterAdapter(TokenSupport.authenticationConverter())))
                        .authenticationEntryPoint((exchange, error) -> error(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "请先登录或重新登录")))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((exchange, exception) -> error(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "请先登录或重新登录"))
                        .accessDeniedHandler((exchange, exception) -> error(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", "当前身份没有权限进入该空间")))
                .build();
    }

    private static Mono<Void> error(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = ("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }
}
