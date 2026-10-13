package com.flowyoga.user.management;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
@Component @Order(Ordered.HIGHEST_PRECEDENCE+10)
public class AccountInternalGuard extends OncePerRequestFilter {
    private final byte[] key;
    public AccountInternalGuard(@Value("${flowyoga.internal.account-token}") String secret) { if(secret==null || secret.length()<32) throw new IllegalStateException("必须配置独立的账号服务内部调用密钥"); this.key=secret.getBytes(StandardCharsets.UTF_8); }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) { return !request.getRequestURI().startsWith("/internal/accounts"); }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String provided=request.getHeader("X-Account-Token");
        if (provided==null || !MessageDigest.isEqual(key,provided.getBytes(StandardCharsets.UTF_8))) { response.setStatus(401); response.setContentType("application/json;charset=UTF-8"); response.getWriter().write("{\"code\":\"UNAUTHORIZED_INTERNAL\",\"message\":\"内部账号调用未通过校验\"}"); return; }
        chain.doFilter(request,response);
    }
}
