package com.flowyoga.course.web;

import com.flowyoga.course.client.MemberCardClient;
import com.flowyoga.course.client.MemberCardProbeResponse;
import com.flowyoga.course.client.MemberServiceCallException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CourseIntegrationController {

    private static final Logger log = LoggerFactory.getLogger(CourseIntegrationController.class);

    private final MemberCardClient memberCardClient;

    public CourseIntegrationController(MemberCardClient memberCardClient) {
        this.memberCardClient = memberCardClient;
    }

    @GetMapping("/api/course/status")
    public CourseStatusResponse status() {
        log.info("course service status");
        return new CourseStatusResponse("flowyoga-course", "UP");
    }

    @GetMapping("/api/course/integration/member-card")
    public CourseMemberChainResponse probeMemberCard() {
        String requestId = currentRequestId();
        try {
            log.info("calling member card probe");
            MemberCardProbeResponse memberCard = memberCardClient.probe();
            if (memberCard == null || !memberCard.probe()) {
                throw new MemberServiceCallException("会员卡服务没有返回可用探针");
            }
            return new CourseMemberChainResponse(
                    "gateway -> flowyoga-course -> flowyoga-member",
                    "flowyoga-course",
                    requestId,
                    memberCard);
        } catch (MemberServiceCallException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MemberServiceCallException("会员卡服务调用失败", exception);
        }
    }

    private static String currentRequestId() {
        String requestId = MDC.get("requestId");
        return requestId == null ? "" : requestId;
    }

    public record CourseStatusResponse(String service, String status) {
    }

    public record CourseMemberChainResponse(
            String path,
            String courseService,
            String requestId,
            MemberCardProbeResponse memberCard) {
    }
}
