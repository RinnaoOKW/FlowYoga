package com.flowyoga.member.management;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.flowyoga.management.*;
import com.flowyoga.management.ManagementModels.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import com.flowyoga.security.TokenSupport;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
@WebMvcTest(MemberManagementController.class)
@Import({DirectorySecurityConfiguration.class,ManagementApiExceptionHandler.class})
@TestPropertySource(properties={"flowyoga.internal.token=dev-internal-token","flowyoga.security.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="})
class MemberManagementControllerTest {
    @Autowired MockMvc mvc; @MockitoBean DirectoryService service; @MockitoBean AccountClient accounts;
    @Test void anonymousAndSpoofedRoleHeadersAreRejected() throws Exception { mvc.perform(get("/api/member/admin/members").header("X-Role","ADMIN")).andExpect(status().isUnauthorized()); }
    @Test void memberAndCoachCannotReadAdminDirectory() throws Exception { for (String role:List.of("MEMBER","COACH")) mvc.perform(get("/api/member/admin/members").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_"+role)))).andExpect(status().isForbidden()); }
    @Test void adminReadsDirectoryThroughService() throws Exception { when(service.list("","",1,10)).thenReturn(new PersonPage(List.of(),0,1,10,new Statistics(0,0,0),List.of())); mvc.perform(get("/api/member/admin/members").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isOk()).andExpect(jsonPath("$.statistics.total").value(0)); }
    @Test void adminWritesRequireCsrfAndInvalidPhoneIsRejected() throws Exception {
        mvc.perform(post("/api/member/admin/members").cookie(adminCookie()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/member/admin/members").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).with(csrf()).contentType("application/json").content("{\"requestId\":\"4e9bb31d-33ab-4771-9e01-64c58bac230a\",\"loginName\":\"test.member\",\"initialPassword\":\"TestYoga@123\",\"displayName\":\"测试\",\"phone\":\"invalid\",\"status\":\"ACTIVE\"}")).andExpect(status().isBadRequest()); verifyNoInteractions(service);
    }
    @Test void revokedCookieIsRejectedEvenWhenCallingServiceDirectly() throws Exception {
        when(accounts.session(any())).thenReturn(new SessionResult(false));
        mvc.perform(get("/api/member/admin/members").cookie(adminCookie()).header("X-Role","ADMIN")).andExpect(status().isUnauthorized()); verifyNoInteractions(service);
    }
    private static Cookie adminCookie() {
        var encoder=new NimbusJwtEncoder(new ImmutableSecret<>(TokenSupport.key("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")));
        var claims=JwtClaimsSet.builder().issuer(TokenSupport.ISSUER).audience(List.of(TokenSupport.AUDIENCE)).subject("1001").id("test-session").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).claim("role","ADMIN").build();
        return new Cookie(TokenSupport.COOKIE_NAME,encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue());
    }
}
