package com.flowyoga.course.client;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import feign.RequestInterceptor;
import feign.RequestTemplate;

@Component
public class OutboundHeadersInterceptor implements RequestInterceptor {

    static final String REQUEST_ID_HEADER = "X-Request-Id";
    static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final String internalToken;

    public OutboundHeadersInterceptor(@Value("${flowyoga.internal.token}") String internalToken) {
        this.internalToken = internalToken;
    }

    @Override
    public void apply(RequestTemplate template) {
        String requestId = MDC.get("requestId");
        if (requestId != null && !requestId.isBlank()) {
            template.header(REQUEST_ID_HEADER, requestId);
        }
        template.header(INTERNAL_TOKEN_HEADER, internalToken);
    }
}
