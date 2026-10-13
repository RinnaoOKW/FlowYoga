package com.flowyoga.management;
import static com.flowyoga.management.ManagementModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import feign.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;

class DirectoryServiceTest {
    private DirectoryStore store; private DirectoryService service; private JdbcTemplate jdbc; private AccountClient accounts;
    private static final Clock CLOCK=Clock.fixed(Instant.parse("2026-10-13T04:00:00Z"),ZoneOffset.UTC);
    private void setup(DirectoryKind kind) {
        var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE "+kind.profileTable+" (id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT UNIQUE,status VARCHAR(16),specialty VARCHAR(255),introduction VARCHAR(1000),version INT DEFAULT 0,created_at TIMESTAMP,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE "+kind.taskTable+" (request_id VARCHAR(36) PRIMARY KEY,"+kind.profileColumn+" BIGINT,user_id BIGINT,login_name VARCHAR(50),operation VARCHAR(16),state VARCHAR(24),payload_json VARCHAR(4000),payload_hash VARCHAR(64),expected_version INT,retry_count INT DEFAULT 0,next_attempt_at TIMESTAMP,locked_until TIMESTAMP,last_error VARCHAR(500),created_at TIMESTAMP,updated_at TIMESTAMP)");
        store=new DirectoryStore(jdbc,new DataSourceTransactionManager(source),kind); accounts=mock(AccountClient.class); service=new DirectoryService(kind,store,accounts,CLOCK,false);
    }
    private static CreatePerson input(UUID id) { return new CreatePerson(id,"test.person","TestYoga@123","测试档案","13900000901","哈他瑜伽","教学简介",Status.ACTIVE); }
    private static AccountView account(DirectoryKind kind) { return new AccountView("9001","test.person","测试档案","13900000901",kind.role,Status.ACTIVE); }
    private void normal(DirectoryKind kind,UUID id) { when(accounts.prepare(eq(kind.role),any())).thenReturn(new ChangeReceipt(id.toString(),"PREPARED",account(kind))); when(accounts.activate(kind.role,id)).thenReturn(new ChangeReceipt(id.toString(),"APPLIED",account(kind))); }
    @ParameterizedTest @EnumSource(DirectoryKind.class) void createsOneProfileAndNeverStoresPlainPassword(DirectoryKind kind) {
        setup(kind); UUID id=UUID.randomUUID(); normal(kind,id);
        assertEquals("DONE",service.create(input(id)).state()); assertEquals("DONE",service.create(input(id)).state());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM "+kind.profileTable,Integer.class));
        assertFalse(store.task(id).payloadJson().contains("TestYoga@123")); verify(accounts,times(1)).prepare(eq(kind.role),any());
    }
    @ParameterizedTest @EnumSource(DirectoryKind.class) void activationTimeoutIsRecoveredWithoutDuplicateProfile(DirectoryKind kind) {
        setup(kind); UUID id=UUID.randomUUID(); normal(kind,id);
        when(accounts.activate(kind.role,id)).thenThrow(remote(503)).thenReturn(new ChangeReceipt(id.toString(),"APPLIED",account(kind)));
        assertEquals("ACCOUNT_CREATED",service.create(input(id)).state());
        when(accounts.change(kind.role,id)).thenReturn(new ChangeReceipt(id.toString(),"APPLIED",account(kind)));
        assertEquals("DONE",service.retry(id).state()); assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM "+kind.profileTable,Integer.class));
    }
    @ParameterizedTest @EnumSource(DirectoryKind.class) void unknownCreationCanBeClosedSafelyAfterRestart(DirectoryKind kind) {
        setup(kind); UUID id=UUID.randomUUID(); when(accounts.prepare(eq(kind.role),any())).thenThrow(remote(503));
        assertEquals("PENDING",service.create(input(id)).state());
        when(accounts.change(kind.role,id)).thenThrow(remote(404)); when(accounts.abort(kind.role,id)).thenReturn(new ChangeReceipt(id.toString(),"ABORTED",null));
        var restarted=new DirectoryService(kind,store,accounts,CLOCK,false);
        assertEquals("COMPENSATED",restarted.retry(id).state()); assertNull(store.forUser(9001)); verify(accounts,never()).activate(any(),any());
    }
    @ParameterizedTest @EnumSource(DirectoryKind.class) void phoneConflictDoesNotCreateProfile(DirectoryKind kind) {
        setup(kind); UUID id=UUID.randomUUID(); when(accounts.prepare(eq(kind.role),any())).thenThrow(remote(409));
        assertThrows(ManagementException.class,()->service.create(input(id))); assertEquals("COMPENSATED",store.task(id).state()); assertNull(store.forUser(9001));
    }
    @ParameterizedTest @EnumSource(DirectoryKind.class) void pendingUpdateBlocksAnotherEditAndRecovers(DirectoryKind kind) {
        setup(kind); UUID create=UUID.randomUUID(); normal(kind,create); service.create(input(create)); Profile profile=store.forUser(9001); when(accounts.account(kind.role,9001)).thenReturn(account(kind));
        UUID edit=UUID.randomUUID(); when(accounts.update(eq(kind.role),eq(9001L),any())).thenThrow(remote(503)).thenReturn(new ChangeReceipt(edit.toString(),"APPLIED",account(kind)));
        assertEquals("PENDING",service.edit(profile.id(),new EditPerson(edit,0,"新姓名","13900000902","新的专长","新的简介")).state());
        assertThrows(ManagementException.class,()->service.status(profile.id(),new SetStatus(UUID.randomUUID(),0,Status.DISABLED)));
        assertEquals("DONE",service.retry(edit).state()); assertEquals(1,store.profile(profile.id()).version());
        if(kind==DirectoryKind.COACH) assertEquals("新的专长",store.profile(profile.id()).specialty());
    }
    @ParameterizedTest @EnumSource(DirectoryKind.class) void staleVersionAndChangedIdempotencyPayloadAreRejected(DirectoryKind kind) {
        setup(kind); UUID create=UUID.randomUUID(); normal(kind,create); service.create(input(create)); Profile profile=store.forUser(9001); when(accounts.account(kind.role,9001)).thenReturn(account(kind));
        UUID change=UUID.randomUUID(); when(accounts.update(eq(kind.role),eq(9001L),any())).thenReturn(new ChangeReceipt(change.toString(),"APPLIED",account(kind)));
        assertEquals("DONE",service.status(profile.id(),new SetStatus(change,0,Status.DISABLED)).state());
        assertThrows(ManagementException.class,()->service.status(profile.id(),new SetStatus(UUID.randomUUID(),0,Status.ACTIVE)));
        var original=input(create); var different=new CreatePerson(create,original.loginName(),original.initialPassword(),"另一个姓名",original.phone(),original.specialty(),original.introduction(),original.status());
        assertThrows(ManagementException.class,()->service.create(different)); verify(accounts,times(1)).update(eq(kind.role),eq(9001L),any());
    }
    private static FeignException remote(int status) { return FeignException.errorStatus("account",Response.builder().status(status).reason("test").request(Request.create(Request.HttpMethod.POST,"http://flowyoga-user",Map.of(),new byte[0],StandardCharsets.UTF_8,null)).headers(Map.of()).body("{\"code\":\"ACCOUNT_CONFLICT\",\"message\":\"手机号已被使用\"}",StandardCharsets.UTF_8).build()); }
}
