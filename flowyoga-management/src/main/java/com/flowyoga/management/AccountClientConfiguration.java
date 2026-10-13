package com.flowyoga.management;
import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
public class AccountClientConfiguration {
    @Bean RequestInterceptor accountHeaders(@Value("${flowyoga.internal.account-token}") String key) {
        if (key==null || key.length()<32) throw new IllegalStateException("必须配置独立的账号服务内部调用密钥");
        return request -> { request.header("X-Account-Token",key); String id=MDC.get("requestId"); if (id!=null) request.header("X-Request-Id",id); };
    }
}
