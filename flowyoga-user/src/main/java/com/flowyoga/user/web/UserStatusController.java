package com.flowyoga.user.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserStatusController {

    private static final Logger log = LoggerFactory.getLogger(UserStatusController.class);

    @GetMapping("/api/user/status")
    public ServiceStatusResponse status() {
        log.info("user service status");
        return new ServiceStatusResponse("flowyoga-user", "UP", "账号与权限将在下一阶段接入");
    }

    public record ServiceStatusResponse(String service, String status, String message) {
    }
}
