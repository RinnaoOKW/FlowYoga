package com.flowyoga.member.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MemberProbeController.class)
@TestPropertySource(properties = "flowyoga.internal.token=dev-internal-token")
class MemberProbeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void statusDoesNotRequireInternalToken() throws Exception {
        mockMvc.perform(get("/api/member/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("flowyoga-member"))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void probeRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/internal/member-cards/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED_INTERNAL"));
    }

    @Test
    void probeEchoesRequestId() throws Exception {
        mockMvc.perform(get("/internal/member-cards/probe")
                        .header(InternalCallGuard.HEADER, "dev-internal-token")
                        .header(RequestIdFilter.HEADER, "chain-test-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("flowyoga-member"))
                .andExpect(jsonPath("$.probe").value(true))
                .andExpect(jsonPath("$.requestId").value("chain-test-001"))
                .andExpect(jsonPath("$.cardType").value("SESSION_CARD"));
    }
}
