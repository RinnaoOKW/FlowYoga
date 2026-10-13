package com.flowyoga.management;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
/** Cookie JWT 使用独立 CSRF 过滤器，避免资源服务器把 Cookie 也视为无须 CSRF 的 Bearer 请求。 */
public final class BrowserCsrf {
    private BrowserCsrf() {}
    public static CsrfFilter filter(CookieCsrfTokenRepository repository) {
        var filter=new CsrfFilter(repository);
        filter.setRequestHandler(new CsrfTokenRequestAttributeHandler());
        filter.setRequireCsrfProtectionMatcher(request -> !request.getRequestURI().startsWith("/internal/") && CsrfFilter.DEFAULT_CSRF_MATCHER.matches(request));
        filter.setAccessDeniedHandler((request,response,error) -> { response.setStatus(403); response.setContentType("application/json;charset=UTF-8"); response.getWriter().write("{\"code\":\"CSRF_INVALID\",\"message\":\"页面校验已失效，请刷新后重新操作\"}"); });
        return filter;
    }
}
