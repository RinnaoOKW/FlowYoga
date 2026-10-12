package com.flowyoga.gateway.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GatewayStatusController {

    @GetMapping("/api/gateway/status")
    public GatewayStatusResponse status() {
        return new GatewayStatusResponse("flowyoga-gateway", "UP");
    }

    public record GatewayStatusResponse(String service, String status) {
    }
}
