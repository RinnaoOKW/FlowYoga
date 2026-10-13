package com.flowyoga.user.management;
import static com.flowyoga.management.ManagementModels.*;
import com.flowyoga.management.ManagementApiExceptionHandler;
import com.flowyoga.security.AuthRole;
import com.flowyoga.user.auth.AuthMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.context.annotation.Import;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
@RestController @Validated
@RequestMapping("/internal/accounts")
@Import(ManagementApiExceptionHandler.class)
public class AccountManagementController {
    private final AccountManagementService service; private final AuthMapper mapper; private final Clock clock;
    public AccountManagementController(AccountManagementService service,AuthMapper mapper,Clock clock) { this.service=service; this.mapper=mapper; this.clock=clock; }
    @PostMapping("/session") public SessionResult session(@Valid @RequestBody SessionQuery input) { return new SessionResult(mapper.activeSession(input.sessionId(),input.userId(),input.role().name(),LocalDateTime.now(clock))==1); }
    @GetMapping("/{role}") public AccountPage search(@PathVariable AuthRole role,@RequestParam(defaultValue="") @Size(max=100) String keyword,@RequestParam(defaultValue="") @Pattern(regexp="|ACTIVE|DISABLED") String status,@RequestParam(defaultValue="1") @Min(1) @Max(100000) int page,@RequestParam(defaultValue="10") @Min(1) @Max(50) int size) { return service.search(role,keyword,status,page,size); }
    @GetMapping("/{role}/{id}") public AccountView account(@PathVariable AuthRole role,@PathVariable @Positive long id) { return service.account(role,id); }
    @PostMapping("/{role}/prepare") public ChangeReceipt prepare(@PathVariable AuthRole role,@Valid @RequestBody CreatePerson input) { return service.prepare(role,input); }
    @GetMapping("/{role}/changes/{id}") public ChangeReceipt change(@PathVariable AuthRole role,@PathVariable UUID id) { return service.change(role,id); }
    @PostMapping("/{role}/changes/{id}/activate") public ChangeReceipt activate(@PathVariable AuthRole role,@PathVariable UUID id) { return service.activate(role,id); }
    @PostMapping("/{role}/changes/{id}/abort") public ChangeReceipt abort(@PathVariable AuthRole role,@PathVariable UUID id) { return service.abort(role,id); }
    @PutMapping("/{role}/{id}") public ChangeReceipt update(@PathVariable AuthRole role,@PathVariable @Positive long id,@Valid @RequestBody UpdateAccount input) { return service.update(role,id,input); }
}
