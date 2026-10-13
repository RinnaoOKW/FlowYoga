package com.flowyoga.management;
import static com.flowyoga.management.ManagementModels.*;
import com.flowyoga.security.AuthRole;
import com.flowyoga.security.TokenSupport;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
@Configuration
@EnableWebSecurity
public class DirectorySecurityConfiguration {
    @Bean JwtDecoder directoryDecoder(@Value("${flowyoga.security.jwt-secret}") String secret,AccountClient accounts) {
        return TokenSupport.decoder(secret,jwt -> {
            try {
                boolean active=accounts.session(new SessionQuery(jwt.getId(),Long.parseLong(jwt.getSubject()),AuthRole.valueOf(jwt.getClaimAsString("role")))).active();
                return active?org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success():TokenSupport.invalid("session is inactive");
            } catch (RuntimeException error) { return TokenSupport.invalid("session could not be verified"); }
        });
    }
    @Bean SecurityFilterChain directorySecurity(HttpSecurity http,JwtDecoder decoder) throws Exception {
        return http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(AbstractHttpConfigurer::disable)
            .addFilterBefore(BrowserCsrf.filter(CookieCsrfTokenRepository.withHttpOnlyFalse()),BearerTokenAuthenticationFilter.class)
            .httpBasic(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.GET,"/api/member/status","/api/course/status","/api/course/integration/member-card","/actuator/health/**","/internal/member-cards/probe").permitAll()
                .requestMatchers("/api/member/admin/**","/api/course/admin/**").hasRole("ADMIN").anyRequest().authenticated())
            .oauth2ResourceServer(resource -> resource.bearerTokenResolver(request -> {
                String header=request.getHeader("Authorization"); if (header!=null && header.startsWith("Bearer ")) return header.substring(7);
                Cookie[] cookies=request.getCookies(); return cookies==null?null:Arrays.stream(cookies).filter(cookie -> cookie.getName().equals(TokenSupport.COOKIE_NAME)).map(Cookie::getValue).findFirst().orElse(null);
            }).jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(TokenSupport.authenticationConverter()))
                .authenticationEntryPoint((request,response,error) -> json(response,401,"UNAUTHENTICATED","登录已失效，请重新登录")))
            .exceptionHandling(errors -> errors.authenticationEntryPoint((request,response,error) -> json(response,401,"UNAUTHENTICATED","请先登录"))
                .accessDeniedHandler((request,response,error) -> json(response,403,"FORBIDDEN","当前身份没有权限执行此操作"))).build();
    }
    private static void json(jakarta.servlet.http.HttpServletResponse response,int status,String code,String message) throws java.io.IOException { response.setStatus(status); response.setContentType("application/json;charset=UTF-8"); response.getWriter().write(PayloadCodec.encode(new ApiError(code,message))); }
}
