package com.flowyoga.management;
import static com.flowyoga.management.ManagementModels.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
@Validated
public abstract class DirectoryController {
    private final DirectoryService service;
    protected DirectoryController(DirectoryService service) { this.service=service; }
    @GetMapping public PersonPage list(@RequestParam(defaultValue="") @Size(max=100) String keyword,
        @RequestParam(defaultValue="") @Pattern(regexp="|ACTIVE|DISABLED") String status,
        @RequestParam(defaultValue="1") @Min(1) @Max(100000) int page,@RequestParam(defaultValue="10") @Min(1) @Max(50) int size) { return service.list(keyword,status,page,size); }
    @PostMapping public ResponseEntity<OperationResult> create(@Valid @RequestBody CreatePerson input) { return response(service.create(input),201); }
    @PutMapping("/{id}") public ResponseEntity<OperationResult> edit(@PathVariable @Positive long id,@Valid @RequestBody EditPerson input) { return response(service.edit(id,input),200); }
    @PatchMapping("/{id}/status") public ResponseEntity<OperationResult> status(@PathVariable @Positive long id,@Valid @RequestBody SetStatus input) { return response(service.status(id,input),200); }
    @PostMapping("/changes/{id}/retry") public ResponseEntity<OperationResult> retry(@PathVariable UUID id) { return response(service.retry(id),200); }
    private static ResponseEntity<OperationResult> response(OperationResult result,int success) { return ResponseEntity.status(result.state().equals("DONE")?success:result.state().equals("COMPENSATED")?409:202).body(result); }
}
