package com.flowyoga.user.management;
import static com.flowyoga.management.ManagementModels.*;
import com.flowyoga.management.*;
import com.flowyoga.security.AuthRole;
import com.flowyoga.user.auth.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
@Service
public class AccountManagementService {
    private final AccountChangeMapper changes; private final AuthMapper users; private final PasswordEncoder passwords; private final Clock clock;
    public AccountManagementService(AccountChangeMapper changes,AuthMapper users,PasswordEncoder passwords,Clock clock) { this.changes=changes; this.users=users; this.passwords=passwords; this.clock=clock; }
    private LocalDateTime now() { return LocalDateTime.now(clock); }
    public AccountPage search(AuthRole role,String keyword,String status,int page,int size) {
        managed(role); String literal=keyword.trim().replace("!","!!").replace("%","!%").replace("_","!_");
        var items=changes.search(role.name(),literal,status,size,(page-1)*size).stream().map(AccountManagementService::view).toList();
        return new AccountPage(items,changes.count(role.name(),literal,status),new Statistics(changes.total(role.name(),""),changes.total(role.name(),"ACTIVE"),changes.total(role.name(),"DISABLED")));
    }
    public AccountView account(AuthRole role,long id) { return view(required(role,id)); }
    @Transactional(isolation=Isolation.READ_COMMITTED) public ChangeReceipt prepare(AuthRole role,CreatePerson input) {
        managed(role);
        if (input.initialPassword().getBytes(StandardCharsets.UTF_8).length>72) throw bad("密码按 UTF-8 编码后不能超过 72 字节");
        ChangePayload payload=new ChangePayload(input.loginName().trim(),input.displayName().trim(),input.phone().trim(),null,null,input.status());
        String hash=PayloadCodec.hash(payload); String id=input.requestId().toString();
        var old=changes.find(id);
        if (old!=null) {
            same(old,role,"CREATE",hash,null);
            if (!passwords.matches(input.initialPassword(),users.findById(old.userId()).passwordHash())) throw conflict("REQUEST_MISMATCH","同一个提交编号不能使用不同的初始密码");
            return receipt(old);
        }
        try { changes.insertChange(id,role.name(),"CREATE","PREPARED",now()); }
        catch (DuplicateKeyException race) {
            // 另一个相同编号的调用先提交：读取它的回执，不能把已成功的请求误报为冲突。
            var committed=changes.find(id); same(committed,role,"CREATE",hash,null);
            if (!passwords.matches(input.initialPassword(),users.findById(committed.userId()).passwordHash())) throw conflict("REQUEST_MISMATCH","同一个提交编号不能使用不同的初始密码");
            return receipt(committed);
        }
        var account=new AccountChangeMapper.NewAccount(); account.loginName=payload.loginName(); account.passwordHash=passwords.encode(input.initialPassword()); account.displayName=payload.displayName(); account.phone=payload.phone(); account.role=role.name(); account.requestId=id; account.now=now();
        try { changes.create(account); } catch (DuplicateKeyException error) { throw conflict("ACCOUNT_CONFLICT","登录账号或手机号已被使用，请更换后重试"); }
        changes.prepare(id,account.id,hash,PayloadCodec.encode(payload),now());
        return receipt(changes.find(id));
    }
    @Transactional public ChangeReceipt change(AuthRole role,UUID id) { return receipt(requiredChange(role,id)); }
    @Transactional public ChangeReceipt activate(AuthRole role,UUID id) {
        var change=requiredChange(role,id);
        if (!change.operation().equals("CREATE")) throw bad("不是账号建档记录");
        if (change.state().equals("ABORTED")) throw conflict("REQUEST_CLOSED","建档请求已关闭，请重新新增");
        if (change.state().equals("PREPARED")) {
            ChangePayload payload=PayloadCodec.decode(change.payloadJson(),ChangePayload.class);
            changes.update(change.userId(),null,null,payload.status().name(),now());
            changes.state(id.toString(),"APPLIED",now());
        }
        return receipt(changes.find(id.toString()));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) public ChangeReceipt abort(AuthRole role,UUID id) {
        managed(role); var change=changes.find(id.toString());
        if (change==null) {
            // 与迟到的 prepare 使用同一主键；唯一键和事务锁保证关闭与创建互斥。
            try { changes.insertChange(id.toString(),role.name(),"CREATE","ABORTED",now()); }
            catch (DuplicateKeyException race) { /* 并发 prepare 已提交，下面锁定其最终记录。 */ }
            change=changes.find(id.toString());
        }
        if (!change.roleCode().equals(role.name()) || !change.operation().equals("CREATE")) throw bad("请求编号与账号身份不匹配");
        if (!change.state().equals("APPLIED")) {
            if (change.userId()!=null) { changes.update(change.userId(),null,null,"DISABLED",now()); changes.revoke(change.userId(),now()); }
            changes.state(id.toString(),"ABORTED",now());
        }
        return receipt(changes.find(id.toString()));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) public ChangeReceipt update(AuthRole role,long userId,UpdateAccount input) {
        managed(role);
        if (input.requestId()==null || (input.displayName()==null && input.phone()==null && input.status()==null)) throw bad("缺少资料修改内容");
        String name=input.displayName()==null?null:input.displayName().trim();
        if (name!=null && name.isBlank()) throw bad("姓名不能为空");
        var payload=new ChangePayload(null,name,input.phone(),null,null,input.status());
        String hash=PayloadCodec.hash(payload); String id=input.requestId().toString();
        var old=changes.find(id);
        if (old!=null) { same(old,role,"UPDATE",hash,userId); return receipt(old); }
        changes.lockUser(userId); required(role,userId);
        old=changes.find(id); // 等待账号锁期间，前一个重试可能已提交，必须再次核实回执。
        if (old!=null) { same(old,role,"UPDATE",hash,userId); return receipt(old); }
        try { changes.insertChange(id,role.name(),"UPDATE","PREPARED",now()); }
        catch (DuplicateKeyException race) { var committed=changes.find(id); same(committed,role,"UPDATE",hash,userId); return receipt(committed); }
        changes.prepare(id,userId,hash,PayloadCodec.encode(payload),now());
        try { changes.update(userId,name,input.phone(),input.status()==null?null:input.status().name(),now()); }
        catch (DuplicateKeyException error) { throw conflict("PHONE_CONFLICT","手机号已被其他账号使用，请检查后重试"); }
        if (input.status()==Status.DISABLED) changes.revoke(userId,now());
        changes.state(id,"APPLIED",now());
        return receipt(changes.find(id));
    }
    private AccountChangeMapper.Change requiredChange(AuthRole role,UUID id) { managed(role); var change=changes.find(id.toString()); if (change==null) throw new ManagementException(HttpStatus.NOT_FOUND,"NOT_FOUND","建档请求不存在"); if (!change.roleCode().equals(role.name())) throw bad("账号身份与请求不匹配"); return change; }
    private UserAccount required(AuthRole role,long id) { managed(role); UserAccount user=users.findById(id); if (user==null) throw new ManagementException(HttpStatus.NOT_FOUND,"NOT_FOUND","账号不存在"); if (user.role()!=role) throw new ManagementException(HttpStatus.FORBIDDEN,"ROLE_MISMATCH","不能修改其他身份的账号"); return user; }
    private void same(AccountChangeMapper.Change change,AuthRole role,String operation,String hash,Long userId) {
        if (change.state().equals("ABORTED")) throw conflict("REQUEST_CLOSED","建档请求已关闭，请重新新增");
        if (!change.roleCode().equals(role.name()) || !change.operation().equals(operation) || !hash.equals(change.payloadHash()) || (userId!=null && !userId.equals(change.userId()))) throw conflict("REQUEST_MISMATCH","同一个提交编号不能修改不同的账号或资料");
    }
    private ChangeReceipt receipt(AccountChangeMapper.Change change) { return new ChangeReceipt(change.requestId(),change.state(),change.userId()==null?null:view(users.findById(change.userId()))); }
    private static AccountView view(UserAccount account) { return new AccountView(Long.toString(account.id()),account.loginName(),account.displayName(),account.phone(),account.role(),Status.valueOf(account.status())); }
    private static void managed(AuthRole role) { if (role!=AuthRole.MEMBER && role!=AuthRole.COACH) throw bad("该接口仅管理会员或教练账号"); }
    private static ManagementException bad(String message) { return new ManagementException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST",message); }
    private static ManagementException conflict(String code,String message) { return new ManagementException(HttpStatus.CONFLICT,code,message); }
}
