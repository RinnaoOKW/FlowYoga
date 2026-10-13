package com.flowyoga.course.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowyoga.course.client.MemberCardClient;
import com.flowyoga.course.client.MemberCardProbeResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import com.flowyoga.management.AccountClient;
import com.flowyoga.management.DirectorySecurityConfiguration;

@WebMvcTest(CourseIntegrationController.class)
@Import(DirectorySecurityConfiguration.class)
@TestPropertySource(properties="flowyoga.security.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
class CourseIntegrationControllerTest {
    @MockitoBean AccountClient accountClient;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberCardClient memberCardClient;

    @Test
    void probeReturnsMemberCardPayload() throws Exception {
        when(memberCardClient.probe()).thenReturn(new MemberCardProbeResponse(
                "flowyoga-member",
                true,
                "SESSION_CARD",
                10,
                "chain-test-001",
                "ok"));

        mockMvc.perform(get("/api/course/integration/member-card")
                        .header(RequestIdFilter.HEADER, "chain-test-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("gateway -> flowyoga-course -> flowyoga-member"))
                .andExpect(jsonPath("$.courseService").value("flowyoga-course"))
                .andExpect(jsonPath("$.requestId").value("chain-test-001"))
                .andExpect(jsonPath("$.memberCard.service").value("flowyoga-member"))
                .andExpect(jsonPath("$.memberCard.probe").value(true));
    }

    @Test
    void probeFailureDoesNotLookSuccessful() throws Exception {
        when(memberCardClient.probe()).thenThrow(new IllegalStateException("timeout"));

        mockMvc.perform(get("/api/course/integration/member-card"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("MEMBER_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("会员卡服务调用失败，本次不视为成功"));
    }
}
