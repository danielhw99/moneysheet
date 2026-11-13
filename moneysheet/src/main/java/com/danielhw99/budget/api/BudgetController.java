package com.danielhw99.budget.api;

import java.time.YearMonth;

import com.danielhw99.auth.security.AuthPrincipal;
import com.danielhw99.budget.service.BudgetService;
import com.danielhw99.common.api.ApiFormats;
import com.danielhw99.common.api.ApiId;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/months/{month}")
public class BudgetController {
    private final BudgetService service;
    public BudgetController(BudgetService service) { this.service = service; }

    @PostMapping("/budget-preparation")
    public ResponseEntity<BudgetDtos.Preparation> prepare(@AuthenticationPrincipal AuthPrincipal principal,
                                                            @PathVariable String month) {
        YearMonth parsed = parseMonth(month);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.prepare(principal.userId(), parsed));
    }

    @GetMapping("/budget")
    public ResponseEntity<BudgetDtos.Summary> get(@AuthenticationPrincipal AuthPrincipal principal,
                                                   @PathVariable String month) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.get(principal.userId(), parseMonth(month)));
    }

    @PutMapping("/budget-setup")
    public ResponseEntity<BudgetDtos.Summary> setup(@AuthenticationPrincipal AuthPrincipal principal,
                                                     @PathVariable String month,
                                                     @RequestBody BudgetDtos.SetupInput input) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.setup(principal.userId(), parseMonth(month), input));
    }

    @PutMapping("/food-budget")
    public ResponseEntity<BudgetDtos.Summary> updateFood(@AuthenticationPrincipal AuthPrincipal principal,
                                                          @PathVariable String month,
                                                          @RequestBody BudgetDtos.Food input) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.updateFood(principal.userId(), parseMonth(month), input));
    }

    @PostMapping("/budget-items")
    public ResponseEntity<BudgetDtos.Item> createItem(@AuthenticationPrincipal AuthPrincipal principal,
                                                       @PathVariable String month,
                                                       @RequestBody BudgetDtos.ItemInput input) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(service.createItem(principal.userId(), parseMonth(month), input));
    }

    @PutMapping("/budget-items/{id}")
    public ResponseEntity<BudgetDtos.Item> updateItem(@AuthenticationPrincipal AuthPrincipal principal,
                                                       @PathVariable String month, @PathVariable String id,
                                                       @RequestBody BudgetDtos.ItemInput input) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.updateItem(principal.userId(), parseMonth(month), parseItemId(id), input));
    }

    @DeleteMapping("/budget-items/{id}")
    public ResponseEntity<Void> deleteItem(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable String month, @PathVariable String id) {
        service.deleteItem(principal.userId(), parseMonth(month), parseItemId(id));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private YearMonth parseMonth(String value) {
        try { return ApiFormats.parseMonth(value); }
        catch (java.time.DateTimeException | IllegalArgumentException invalid) {
            throw new ApiException(ErrorCode.VALIDATION);
        }
    }

    private long parseItemId(String value) {
        try { return ApiId.parse(value).value(); }
        catch (IllegalArgumentException invalid) { throw new ApiException(ErrorCode.VALIDATION); }
    }
}
