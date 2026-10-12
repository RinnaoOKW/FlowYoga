package com.flowyoga.course.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "flowyoga-member", path = "/internal/member-cards")
public interface MemberCardClient {

    @GetMapping("/probe")
    MemberCardProbeResponse probe();
}
