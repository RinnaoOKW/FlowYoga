package com.flowyoga.user.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.flowyoga.user.auth.AuthMapper;
import com.flowyoga.user.auth.UserSecurityConfiguration;

@WebMvcTest(UserStatusController.class)
@Import(UserSecurityConfiguration.class)
@TestPropertySource(properties = {"flowyoga.security.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=","flowyoga.internal.account-token=test-account-internal-secret-32-bytes"})
class UserStatusControllerTest {

    @MockitoBean
    private AuthMapper authMapper;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void statusIsUp() throws Exception {
        mockMvc.perform(get("/api/user/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("flowyoga-user"))
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
