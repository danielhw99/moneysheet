package com.danielhw99.payment.api;

import java.time.YearMonth;
import com.danielhw99.payment.service.PaymentReadService;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.RequestParam;
import com.danielhw99.auth.security.AuthPrincipal;
import com.danielhw99.common.api.ApiId;
import com.danielhw99.payment.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
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
@RequestMapping("/api/v1/months/{month}/fixed-payments")
public class PaymentController {
    private final PaymentService service;
    private final PaymentReadService reads;
    public PaymentController(PaymentService service, PaymentReadService reads) { this.service = service; this.reads = reads; }
    @GetMapping
    public ResponseEntity<PaymentList> list(@AuthenticationPrincipal AuthPrincipal user,
            @Parameter(schema=@Schema(type="string",pattern="^[0-9]{4}-(0[1-9]|1[0-2])$")) @PathVariable YearMonth month,
            @RequestParam(required=false) LocalDate date,
            @RequestParam(required=false) ApiId budgetItemId,
            @RequestParam(required=false) String cursor,
            @RequestParam(defaultValue="50") @Min(1) @Max(100) int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reads.list(user.userId(), month, date,
                budgetItemId == null ? null : budgetItemId.value(), cursor, limit));
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<PaymentResponse> create(@AuthenticationPrincipal AuthPrincipal user,
            @Parameter(schema = @Schema(type = "string", pattern = "^[0-9]{4}-(0[1-9]|1[0-2])$")) @PathVariable YearMonth month, @Valid @RequestBody PaymentInput input) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(service.create(user.userId(), month, input));
    }
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> get(@AuthenticationPrincipal AuthPrincipal user,
            @Parameter(schema = @Schema(type = "string", pattern = "^[0-9]{4}-(0[1-9]|1[0-2])$")) @PathVariable YearMonth month, @PathVariable ApiId id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(user.userId(), month, id.value()));
    }
    @PutMapping("/{id}")
    public ResponseEntity<PaymentResponse> update(@AuthenticationPrincipal AuthPrincipal user,
            @Parameter(schema = @Schema(type = "string", pattern = "^[0-9]{4}-(0[1-9]|1[0-2])$")) @PathVariable YearMonth month, @PathVariable ApiId id, @Valid @RequestBody PaymentInput input) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.update(user.userId(), month, id.value(), input));
    }
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthPrincipal user,
            @Parameter(schema = @Schema(type = "string", pattern = "^[0-9]{4}-(0[1-9]|1[0-2])$")) @PathVariable YearMonth month, @PathVariable ApiId id) {
        service.delete(user.userId(), month, id.value());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
