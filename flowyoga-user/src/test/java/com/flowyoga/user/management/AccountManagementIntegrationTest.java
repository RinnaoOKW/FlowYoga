package com.flowyoga.user.management;
import static com.flowyoga.management.ManagementModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.flowyoga.management.*;
import com.flowyoga.security.AuthRole;
import com.flowyoga.user.auth.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:account_management;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.cloud.nacos.discovery.enabled=false","spring.cloud.discovery.enabled=false","flowyoga.security.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=","flowyoga.demo.enabled=true","flowyoga.internal.account-token=test-account-internal-secret-32-bytes"})
@AutoConfigureMockMvc
class AccountManagementIntegrationTest {
    @Autowired AccountManagementService service; @Autowired AuthService auth; @Autowired JdbcTemplate jdbc; @Autowired MockMvc mvc;
    @BeforeEach void reset() { jdbc.update("DELETE FROM account_change"); jdbc.update("DELETE FROM auth_session"); jdbc.update("DELETE FROM sys_user WHERE id NOT IN (1001,2001,3001)"); jdbc.update("UPDATE sys_user SET status='ACTIVE'"); jdbc.update("UPDATE sys_user SET display_name='陈教练',phone='13000000002' WHERE id=2001"); jdbc.update("UPDATE sys_user SET display_name='瑜伽会员',phone='13000000003' WHERE id=3001"); }
    private CreatePerson input(UUID id,String name,String phone) { return new CreatePerson(id,name,"TestYoga@123","测试人员",phone,null,null,Status.ACTIVE); }
    @Test void preparedAccountCannotLoginUntilProfileActivation() {
        UUID id=UUID.randomUUID(); var prepared=service.prepare(AuthRole.MEMBER,input(id,"new.member","13900000801")); assertEquals(Status.DISABLED,prepared.account().status());
        assertThrows(AuthException.class,()->auth.login("new.member","TestYoga@123",AuthRole.MEMBER));
        service.activate(AuthRole.MEMBER,id); assertEquals(AuthRole.MEMBER,auth.login("new.member","TestYoga@123",AuthRole.MEMBER).user().role());
    }
    @Test void sameRequestCreatesOneAccountAndChangedPayloadIsRejected() {
        UUID id=UUID.randomUUID(); var command=input(id,"new.coach","13900000802"); var first=service.prepare(AuthRole.COACH,command); var second=service.prepare(AuthRole.COACH,command);
        assertEquals(first.account().id(),second.account().id()); assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE creation_request_id=?",Integer.class,id.toString()));
        assertThrows(ManagementException.class,()->service.prepare(AuthRole.COACH,input(id,"new.coach","13900000803")));
    }
    @Test void globalPhoneUniquenessRollsBackEntireCreation() {
        UUID id=UUID.randomUUID(); assertThrows(ManagementException.class,()->service.prepare(AuthRole.COACH,input(id,"new.coach","13000000003")));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM account_change WHERE request_id=?",Integer.class,id.toString())); assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE login_name='new.coach'",Integer.class));
    }
    @Test void concurrentCreationRetriesReturnTheSameAccount() throws Exception {
        UUID id=UUID.randomUUID(); var command=input(id,"parallel.member","13900000809");
        var results=concurrent(()->service.prepare(AuthRole.MEMBER,command));
        assertEquals(results[0].account().id(),results[1].account().id());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE creation_request_id=?",Integer.class,id.toString()));
    }
    @Test void concurrentUpdateRetriesApplyOneReceipt() throws Exception {
        UUID id=UUID.randomUUID(); var command=new UpdateAccount(id,"并发修改","13900000810",null);
        var results=concurrent(()->service.update(AuthRole.COACH,2001,command));
        assertEquals("APPLIED",results[0].state()); assertEquals("APPLIED",results[1].state());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM account_change WHERE request_id=?",Integer.class,id.toString()));
    }
    private ChangeReceipt[] concurrent(Supplier<ChangeReceipt> action) throws Exception {
        var barrier=new CyclicBarrier(2);
        Supplier<ChangeReceipt> simultaneous=()->{ try { barrier.await(5,TimeUnit.SECONDS); return action.get(); } catch (Exception failure) { throw new RuntimeException(failure); } };
        var first=CompletableFuture.supplyAsync(simultaneous); var second=CompletableFuture.supplyAsync(simultaneous);
        return new ChangeReceipt[]{first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS)};
    }
    @Test void abortTombstonePreventsLateCreationAndDoesNotTouchExistingAccount() {
        UUID id=UUID.randomUUID(); assertEquals("ABORTED",service.abort(AuthRole.MEMBER,id).state());
        assertThrows(ManagementException.class,()->service.prepare(AuthRole.MEMBER,input(id,"late.member","13900000804")));
        assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM sys_user WHERE id=3001",String.class));
    }
    @Test void abortOfPreparedAccountKeepsItDisabledButCannotDisableAppliedAccount() {
        UUID id=UUID.randomUUID(); var prepared=service.prepare(AuthRole.MEMBER,input(id,"first.member","13900000805")); service.abort(AuthRole.MEMBER,id); assertEquals("DISABLED",jdbc.queryForObject("SELECT status FROM sys_user WHERE id=?",String.class,Long.parseLong(prepared.account().id())));
        UUID applied=UUID.randomUUID(); service.prepare(AuthRole.COACH,input(applied,"first.coach","13900000806")); service.activate(AuthRole.COACH,applied); assertEquals("APPLIED",service.abort(AuthRole.COACH,applied).state()); assertEquals(Status.ACTIVE,service.change(AuthRole.COACH,applied).account().status());
    }
    @Test void disableRevokesSessionsAndReenableDoesNotResurrectThem() {
        auth.login("member","FlowYoga@123",AuthRole.MEMBER); assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM auth_session WHERE revoked_at IS NULL",Integer.class));
        service.update(AuthRole.MEMBER,3001,new UpdateAccount(UUID.randomUUID(),null,null,Status.DISABLED)); assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM auth_session WHERE revoked_at IS NULL",Integer.class));
        service.update(AuthRole.MEMBER,3001,new UpdateAccount(UUID.randomUUID(),null,null,Status.ACTIVE)); assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM auth_session WHERE revoked_at IS NULL",Integer.class)); auth.login("member","FlowYoga@123",AuthRole.MEMBER);
    }
    @Test void updateRetryCannotOverwriteLaterAcceptedChanges() {
        UUID old=UUID.randomUUID(); var input=new UpdateAccount(old,"第一次修改","13900000807",null); service.update(AuthRole.COACH,2001,input); service.update(AuthRole.COACH,2001,new UpdateAccount(UUID.randomUUID(),"第二次修改",null,null)); service.update(AuthRole.COACH,2001,input);
        assertEquals("第二次修改",service.account(AuthRole.COACH,2001).displayName()); assertThrows(ManagementException.class,()->service.update(AuthRole.MEMBER,2001,new UpdateAccount(UUID.randomUUID(),"错误身份",null,null)));
    }
    @Test void searchTreatsPercentAndUnderscoreAsLiteralAndNeverReturnsPassword() throws Exception {
        assertEquals(0,service.search(AuthRole.MEMBER,"%","",1,10).total()); assertEquals(0,service.search(AuthRole.MEMBER,"_","",1,10).total());
        mvc.perform(get("/internal/accounts/MEMBER").header("X-Account-Token","test-account-internal-secret-32-bytes")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].phone").exists()).andExpect(jsonPath("$.items[0].passwordHash").doesNotExist());
    }
    @Test void internalEndpointRejectsBrowserRoleHeadersAndCannotCreateAdmin() throws Exception {
        mvc.perform(get("/internal/accounts/MEMBER").header("X-Role","ADMIN")).andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/accounts/ADMIN/prepare").header("X-Account-Token","test-account-internal-secret-32-bytes").contentType("application/json").content(PayloadCodec.encode(input(UUID.randomUUID(),"fake.admin","13900000808")))).andExpect(status().isBadRequest());
    }
}
