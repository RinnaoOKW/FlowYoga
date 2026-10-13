package com.flowyoga.management;
import static com.flowyoga.management.ManagementModels.*;
import feign.FeignException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;

public class DirectoryService {
    private final DirectoryKind kind; private final DirectoryStore store; private final AccountClient accounts; private final Clock clock; private final boolean demo;
    public DirectoryService(DirectoryKind kind,DirectoryStore store,AccountClient accounts,Clock clock,boolean demo) { this.kind=kind; this.store=store; this.accounts=accounts; this.clock=clock; this.demo=demo; }
    private LocalDateTime now() { return LocalDateTime.now(clock); }
    public PersonPage list(String keyword,String status,int page,int size) {
        try {
            AccountPage found=accounts.search(kind.role,keyword.trim(),status,page,size);
            var items=found.items().stream().map(account -> {
                long userId=Long.parseLong(account.id()); Profile profile=store.forUser(userId);
                return new PersonView(profile==null?null:Long.toString(profile.id()),account.id(),account.loginName(),account.displayName(),account.phone(),profile==null?account.status().name():profile.status(),account.status(),profile==null?null:profile.specialty(),profile==null?null:profile.introduction(),profile==null?0:profile.version(),profile==null?"PROFILE_PENDING":store.pendingFor(userId)?"PENDING":"DONE",profile==null?null:profile.createdAt());
            }).toList();
            var pending=store.pending().stream().map(task -> new TaskView(task.requestId(),task.operation(),task.state(),task.loginName(),task.retryCount(),task.lastError(),task.updatedAt())).toList();
            return new PersonPage(items,found.total(),page,size,found.statistics(),pending);
        } catch (FeignException error) { throw unavailable(); }
    }
    public OperationResult create(CreatePerson input) {
        ChangePayload payload=new ChangePayload(input.loginName().trim(),input.displayName().trim(),input.phone().trim(),clean(input.specialty()),clean(input.introduction()),input.status());
        store.stage(input.requestId(),"CREATE",null,payload.loginName(),payload,0,now());
        return process(input.requestId(),new CreatePerson(input.requestId(),payload.loginName(),input.initialPassword(),payload.displayName(),payload.phone(),payload.specialty(),payload.introduction(),payload.status()));
    }
    public OperationResult edit(long id,EditPerson input) {
        Profile profile=required(id);
        ChangePayload payload=new ChangePayload(null,input.displayName().trim(),input.phone().trim(),clean(input.specialty()),clean(input.introduction()),null);
        stageUpdate(profile,input.requestId(),"UPDATE",payload,input.version());
        return process(input.requestId(),null);
    }
    public OperationResult status(long id,SetStatus input) {
        Profile profile=required(id);
        stageUpdate(profile,input.requestId(),"STATUS",new ChangePayload(null,null,null,null,null,input.status()),input.version());
        return process(input.requestId(),null);
    }
    private void stageUpdate(Profile profile,UUID id,String operation,ChangePayload payload,int version) {
        try { var account=accounts.account(kind.role,profile.userId()); store.stage(id,operation,profile,account.loginName(),payload,version,now()); }
        catch (FeignException error) { throw unavailable(); }
    }
    private Profile required(long id) { Profile profile=store.profile(id); if (profile==null) throw new ManagementException(HttpStatus.NOT_FOUND,"NOT_FOUND","该档案不存在，请刷新列表"); return profile; }
    public OperationResult retry(UUID id) { if (store.task(id)==null) throw new ManagementException(HttpStatus.NOT_FOUND,"NOT_FOUND","同步记录不存在"); return process(id,null); }
    private OperationResult process(UUID id,CreatePerson initial) {
        Task before=store.task(id);
        if (before.state().equals("DONE") || before.state().equals("COMPENSATED") || !store.claim(id,now())) return result(store.task(id));
        Task task=store.task(id); ChangePayload payload=PayloadCodec.decode(task.payloadJson(),ChangePayload.class);
        try {
            if (task.operation().equals("CREATE")) {
                ChangeReceipt receipt;
                if (initial!=null && task.state().equals("PENDING")) receipt=accounts.prepare(kind.role,initial);
                else {
                    try { receipt=accounts.change(kind.role,id); }
                    catch (FeignException missing) { if (missing.status()!=404) throw missing; receipt=accounts.abort(kind.role,id); }
                }
                if (receipt.state().equals("ABORTED")) { store.reject(id,"建档未完成，账号已安全关闭，请重新新增",now()); return result(store.task(id)); }
                store.prepareProfile(id,Long.parseLong(receipt.account().id()),payload,now());
                accounts.activate(kind.role,id);
                store.done(id,now());
            } else {
                accounts.update(kind.role,task.userId(),new UpdateAccount(id,payload.displayName(),payload.phone(),payload.status()));
                store.applyProfile(task,payload,now());
            }
        } catch (FeignException error) {
            if (error.status()==400 || error.status()==409) {
                String message="账号或手机号已被使用，请检查后重新提交"; String code="ACCOUNT_CONFLICT";
                try { ApiError detail=PayloadCodec.decode(error.contentUTF8(),ApiError.class); message=detail.message(); code=detail.code(); } catch (RuntimeException ignored) {}
                // 用户服务的明确拒绝发生在本地事务回滚之后，不会留下已生效的修改。
                store.reject(id,message,now());
                throw new ManagementException(HttpStatus.valueOf(error.status()),code,message);
            }
            store.failed(id,"账号服务暂时不可用，系统将继续同步",now());
        } catch (RuntimeException error) {
            store.failed(id,"资料同步尚未完成，系统将重试，请勿重复建档",now());
        }
        return result(store.task(id));
    }
    private OperationResult result(Task task) {
        String message=switch(task.state()) { case "DONE" -> "资料已保存"; case "COMPENSATED" -> task.lastError(); default -> "已接收，资料同步处理中，请稍后刷新"; };
        return new OperationResult(task.requestId(),task.state(),task.profileId()==null?null:task.profileId().toString(),message);
    }
    @Scheduled(fixedDelayString="${flowyoga.management.retry-interval-ms:5000}",initialDelay=5000)
    public void recover() { for (Task task:store.due(now())) { try { process(UUID.fromString(task.requestId()),null); } catch (RuntimeException ignored) { /* 拒绝已留档；未完成任务将在下一次扫描中继续处理。 */ } } }
    @Scheduled(fixedDelay=30000,initialDelay=5000)
    public void seedDemoProfile() {
        if (!demo) return;
        String login=kind==DirectoryKind.MEMBER?"member":"coach";
        try { accounts.search(kind.role,login,"",1,50).items().stream().filter(account -> account.loginName().equals(login)).forEach(account -> store.seed(account,now())); } catch (RuntimeException ignored) { /* 用户服务启动较晚时，下轮重试。 */ }
    }
    private static String clean(String text) { return text==null?null:text.trim(); }
    private static ManagementException unavailable() { return new ManagementException(HttpStatus.SERVICE_UNAVAILABLE,"ACCOUNT_SERVICE_UNAVAILABLE","账号服务暂时不可用，请稍后重试"); }
}
