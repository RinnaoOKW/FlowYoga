package com.flowyoga.course.management;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.flowyoga.management.*;
import com.flowyoga.management.ManagementModels.*;
import com.flowyoga.course.client.MemberCardClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
@WebMvcTest(CoachManagementController.class)
@Import({DirectorySecurityConfiguration.class,ManagementApiExceptionHandler.class})
@TestPropertySource(properties="flowyoga.security.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
class CoachManagementControllerTest {
    @Autowired MockMvc mvc; @MockitoBean DirectoryService service; @MockitoBean AccountClient accounts; @MockitoBean MemberCardClient cards;
    @Test void anonymousCannotReadCoaches() throws Exception { mvc.perform(get("/api/course/admin/coaches")).andExpect(status().isUnauthorized()); }
    @Test void memberAndCoachCannotReadAdminDirectory() throws Exception { for (String role:List.of("MEMBER","COACH")) mvc.perform(get("/api/course/admin/coaches").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_"+role)))).andExpect(status().isForbidden()); }
    @Test void adminReadsCoaches() throws Exception { when(service.list("","",1,10)).thenReturn(new PersonPage(List.of(),0,1,10,new Statistics(0,0,0),List.of())); mvc.perform(get("/api/course/admin/coaches").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isOk()); }
    @Test void writesRequireCsrfAndInvalidPaginationIsRejected() throws Exception {
        mvc.perform(patch("/api/course/admin/coaches/1/status").with(user("admin").roles("ADMIN")).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/course/admin/coaches?size=1000").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isBadRequest()); verifyNoInteractions(service);
    }
}
