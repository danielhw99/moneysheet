package com.danielhw99.transaction.api;

import java.time.YearMonth;
import com.danielhw99.auth.security.AuthPrincipal;
import com.danielhw99.common.api.ApiId;
import com.danielhw99.transaction.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/months/{month}/transactions")
public class TransactionController {
    private final TransactionService service;
    public TransactionController(TransactionService service) { this.service = service; }
    @PostMapping
    public ResponseEntity<TransactionResponse> create(@AuthenticationPrincipal AuthPrincipal user,
            @PathVariable YearMonth month, @Valid @RequestBody TransactionInput input) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(service.create(user.userId(), month, input));
    }
    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> get(@AuthenticationPrincipal AuthPrincipal user,
            @PathVariable YearMonth month, @PathVariable ApiId id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(user.userId(), month, id.value()));
    }
    @PutMapping("/{id}")
    public ResponseEntity<TransactionResponse> update(@AuthenticationPrincipal AuthPrincipal user,
            @PathVariable YearMonth month, @PathVariable ApiId id, @Valid @RequestBody TransactionInput input) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.update(user.userId(), month, id.value(), input));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthPrincipal user,
            @PathVariable YearMonth month, @PathVariable ApiId id) {
        service.delete(user.userId(), month, id.value());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
