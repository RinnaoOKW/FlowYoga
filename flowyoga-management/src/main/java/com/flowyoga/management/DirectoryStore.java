package com.flowyoga.management;
import static com.flowyoga.management.ManagementModels.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

public class DirectoryStore {
    private final JdbcTemplate jdbc; private final DirectoryKind kind; private final TransactionTemplate tx;
    public DirectoryStore(JdbcTemplate jdbc,PlatformTransactionManager manager,DirectoryKind kind) {
        this.jdbc=jdbc; this.kind=kind; this.tx=new TransactionTemplate(manager);
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    public Profile profile(long id) { return jdbc.query(profileSql()+" WHERE id=?",this::readProfile,id).stream().findFirst().orElse(null); }
    public Profile forUser(long userId) { return jdbc.query(profileSql()+" WHERE user_id=?",this::readProfile,userId).stream().findFirst().orElse(null); }
    private String profileSql() { return "SELECT id,user_id,status,version,created_at,"+(kind==DirectoryKind.COACH ? "specialty,introduction" : "NULL AS specialty,NULL AS introduction")+" FROM "+kind.profileTable; }
    private Profile readProfile(ResultSet row,int n) throws SQLException { return new Profile(row.getLong("id"),row.getLong("user_id"),row.getString("status"),row.getString("specialty"),row.getString("introduction"),row.getInt("version"),row.getTimestamp("created_at").toLocalDateTime()); }
    private String taskSql() { return "SELECT *,"+kind.profileColumn+" AS profile_id FROM "+kind.taskTable; }
    private Task readTask(ResultSet row,int n) throws SQLException { return new Task(row.getString("request_id"),row.getString("operation"),row.getString("state"),(Long)row.getObject("profile_id"),(Long)row.getObject("user_id"),row.getString("login_name"),row.getString("payload_json"),row.getString("payload_hash"),row.getInt("expected_version"),row.getInt("retry_count"),row.getString("last_error"),row.getTimestamp("updated_at").toLocalDateTime()); }
    public Task task(UUID id) { return jdbc.query(taskSql()+" WHERE request_id=?",this::readTask,id.toString()).stream().findFirst().orElse(null); }
    public List<Task> pending() { return jdbc.query(taskSql()+" WHERE state IN ('PENDING','ACCOUNT_CREATED') ORDER BY created_at LIMIT 50",this::readTask); }
    public List<Task> due(LocalDateTime now) { return jdbc.query(taskSql()+" WHERE state IN ('PENDING','ACCOUNT_CREATED') AND (next_attempt_at IS NULL OR next_attempt_at<=?) AND (locked_until IS NULL OR locked_until<?) ORDER BY created_at LIMIT 20",this::readTask,now,now); }
    public boolean pendingFor(long userId) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+kind.taskTable+" WHERE user_id=? AND state IN ('PENDING','ACCOUNT_CREATED')",Integer.class,userId)>0; }
    public Task stage(UUID id,String operation,Profile profile,String loginName,ChangePayload payload,int expectedVersion,LocalDateTime now) {
        String hash=PayloadCodec.hash(List.of(operation,profile==null?0:profile.id(),expectedVersion,payload));
        return tx.execute(status -> {
            if (profile!=null) {
                jdbc.queryForList("SELECT id FROM "+kind.profileTable+" WHERE id=? FOR UPDATE",profile.id());
            }
            Task old=task(id);
            if (old!=null) {
                if (!old.payloadHash().equals(hash)) throw new ManagementException(HttpStatus.CONFLICT,"REQUEST_MISMATCH","同一个提交编号不能用于不同的资料，请重新提交");
                return old;
            }
            if (profile!=null) {
                Profile current=profile(profile.id());
                if (current.version()!=expectedVersion) throw new ManagementException(HttpStatus.CONFLICT,"STALE_PROFILE","资料已被修改，请刷新列表后重新编辑");
                if (pendingFor(current.userId())) throw new ManagementException(HttpStatus.CONFLICT,"OPERATION_PENDING","该档案尚有资料同步未完成，请先完成同步");
            }
            jdbc.update("INSERT INTO "+kind.taskTable+" (request_id,operation,"+kind.profileColumn+",user_id,login_name,payload_json,payload_hash,expected_version,state,created_at,updated_at,next_attempt_at) VALUES (?,?,?,?,?,?,?,?,'PENDING',?,?,?)",id.toString(),operation,profile==null?null:profile.id(),profile==null?null:profile.userId(),loginName,PayloadCodec.encode(payload),hash,expectedVersion,now,now,now);
            return task(id);
        });
    }
    public boolean claim(UUID id,LocalDateTime now) { return jdbc.update("UPDATE "+kind.taskTable+" SET locked_until=? WHERE request_id=? AND state IN ('PENDING','ACCOUNT_CREATED') AND (locked_until IS NULL OR locked_until<?)",now.plusSeconds(60),id.toString(),now)==1; }
    public void prepareProfile(UUID id,long userId,ChangePayload payload,LocalDateTime now) {
        tx.executeWithoutResult(status -> {
            if (forUser(userId)==null) insertProfile(userId,payload,now);
            Profile profile=forUser(userId);
            jdbc.update("UPDATE "+kind.taskTable+" SET "+kind.profileColumn+"=?,user_id=?,state='ACCOUNT_CREATED',updated_at=? WHERE request_id=?",profile.id(),userId,now,id.toString());
        });
    }
    private void insertProfile(long userId,ChangePayload payload,LocalDateTime now) {
        if (kind==DirectoryKind.COACH) jdbc.update("INSERT INTO coach_profile (user_id,status,specialty,introduction,created_at,updated_at) VALUES (?,?,?,?,?,?)",userId,payload.status().name(),payload.specialty(),payload.introduction(),now,now);
        else jdbc.update("INSERT INTO member_profile (user_id,status,created_at,updated_at) VALUES (?,?,?,?)",userId,payload.status().name(),now,now);
    }
    public void applyProfile(Task task,ChangePayload payload,LocalDateTime now) {
        tx.executeWithoutResult(status -> {
            int changed;
            if (task.operation().equals("STATUS")) changed=jdbc.update("UPDATE "+kind.profileTable+" SET status=?,version=version+1,updated_at=? WHERE id=? AND version=?",payload.status().name(),now,task.profileId(),task.expectedVersion());
            else if (kind==DirectoryKind.COACH) changed=jdbc.update("UPDATE coach_profile SET specialty=?,introduction=?,version=version+1,updated_at=? WHERE id=? AND version=?",payload.specialty(),payload.introduction(),now,task.profileId(),task.expectedVersion());
            else changed=jdbc.update("UPDATE member_profile SET version=version+1,updated_at=? WHERE id=? AND version=?",now,task.profileId(),task.expectedVersion());
            if (changed!=1) throw new IllegalStateException("资料版本与同步任务不符，需要核对");
            done(UUID.fromString(task.requestId()),now);
        });
    }
    public void done(UUID id,LocalDateTime now) { jdbc.update("UPDATE "+kind.taskTable+" SET state='DONE',locked_until=NULL,last_error=NULL,updated_at=? WHERE request_id=?",now,id.toString()); }
    public void reject(UUID id,String message,LocalDateTime now) {
        tx.executeWithoutResult(status -> {
            Task task=task(id);
            if (task.operation().equals("CREATE") && task.profileId()!=null) jdbc.update("UPDATE "+kind.profileTable+" SET status='DISABLED',version=version+1,updated_at=? WHERE id=?",now,task.profileId());
            jdbc.update("UPDATE "+kind.taskTable+" SET state='COMPENSATED',locked_until=NULL,last_error=?,updated_at=? WHERE request_id=?",message,now,id.toString());
        });
    }
    public void failed(UUID id,String message,LocalDateTime now) {
        Task task=task(id); long delay=Math.min(300,5L << Math.min(task.retryCount(),6));
        jdbc.update("UPDATE "+kind.taskTable+" SET retry_count=retry_count+1,next_attempt_at=?,locked_until=NULL,last_error=?,updated_at=? WHERE request_id=? AND state IN ('PENDING','ACCOUNT_CREATED')",now.plusSeconds(delay),message,now,id.toString());
    }
    public void seed(AccountView account,LocalDateTime now) {
        tx.executeWithoutResult(status -> { if (forUser(Long.parseLong(account.id()))==null) insertProfile(Long.parseLong(account.id()),new ChangePayload(account.loginName(),account.displayName(),account.phone(),kind==DirectoryKind.COACH?"基础瑜伽、哈他瑜伽":null,kind==DirectoryKind.COACH?"FlowYoga 本地演示教练":null,account.status()),now); });
    }
}
