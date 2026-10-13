package com.flowyoga.management;
import static com.flowyoga.management.ManagementModels.*;
import com.flowyoga.security.AuthRole;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
@FeignClient(name="flowyoga-user", contextId="managementAccountClient", configuration=AccountClientConfiguration.class)
public interface AccountClient {
    @PostMapping("/internal/accounts/session") SessionResult session(@RequestBody SessionQuery input);
    @GetMapping("/internal/accounts/{role}") AccountPage search(@PathVariable AuthRole role,@RequestParam String keyword,@RequestParam String status,@RequestParam int page,@RequestParam int size);
    @GetMapping("/internal/accounts/{role}/{id}") AccountView account(@PathVariable AuthRole role,@PathVariable long id);
    @PostMapping("/internal/accounts/{role}/prepare") ChangeReceipt prepare(@PathVariable AuthRole role,@RequestBody CreatePerson input);
    @GetMapping("/internal/accounts/{role}/changes/{id}") ChangeReceipt change(@PathVariable AuthRole role,@PathVariable UUID id);
    @PostMapping("/internal/accounts/{role}/changes/{id}/activate") ChangeReceipt activate(@PathVariable AuthRole role,@PathVariable UUID id);
    @PostMapping("/internal/accounts/{role}/changes/{id}/abort") ChangeReceipt abort(@PathVariable AuthRole role,@PathVariable UUID id);
    @PutMapping("/internal/accounts/{role}/{id}") ChangeReceipt update(@PathVariable AuthRole role,@PathVariable long id,@RequestBody UpdateAccount input);
}
