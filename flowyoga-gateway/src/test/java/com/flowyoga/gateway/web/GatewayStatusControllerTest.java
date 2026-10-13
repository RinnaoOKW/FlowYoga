package com.flowyoga.gateway.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import com.flowyoga.gateway.config.GatewaySecurityConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = GatewayStatusController.class)
@Import({GatewayRequestIdFilter.class, GatewaySecurityConfiguration.class})
@TestPropertySource(properties = "flowyoga.security.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
class GatewayStatusControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void statusIncludesGeneratedRequestId() {
        webTestClient.get()
                .uri("/api/gateway/status")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueMatches("X-Request-Id", "[A-Za-z0-9-]{8,64}")
                .expectBody()
                .jsonPath("$.service").isEqualTo("flowyoga-gateway")
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void keepsCallerRequestId() {
        webTestClient.get()
                .uri("/api/gateway/status")
                .header(GatewayRequestIdFilter.HEADER, "chain-test-001")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(GatewayRequestIdFilter.HEADER, "chain-test-001");
    }

    @Test
    void gatewayRejectsAnonymousWorkspaceRequests() {
        webTestClient.get().uri("/api/user/workspaces/admin").header("X-Role", "ADMIN")
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void gatewayRejectsInvalidBearerToken() {
        webTestClient.get().uri("/api/user/auth/me").header("Authorization", "Bearer invalid-token")
                .exchange().expectStatus().isUnauthorized();
    }
}
