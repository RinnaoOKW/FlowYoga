package com.flowyoga.member.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MemberProbeController {

    private static final Logger log = LoggerFactory.getLogger(MemberProbeController.class);

    @GetMapping("/api/member/status")
    public MemberStatusResponse status() {
        log.info("member service status");
        return new MemberStatusResponse("flowyoga-member", "UP");
    }

    /**
     * 最小调用链探针。固定返回示例课次，不读取会员卡数据。
     */
    @GetMapping("/internal/member-cards/probe")
    public MemberCardProbeResponse probe() {
        String requestId = MDC.get("requestId");
        log.info("member card probe");
        return new MemberCardProbeResponse(
                "flowyoga-member",
                true,
                "SESSION_CARD",
                10,
                requestId == null ? "" : requestId,
                "会员卡服务可达。这是固定探针数据，不是会员的真实剩余次数。");
    }

    public record MemberStatusResponse(String service, String status) {
    }

    public record MemberCardProbeResponse(
            String service,
            boolean probe,
            String cardType,
            int remainingSessions,
            String requestId,
            String message) {
    }
}
