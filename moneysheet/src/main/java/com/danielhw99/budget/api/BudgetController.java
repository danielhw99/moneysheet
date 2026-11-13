package com.danielhw99.budget.api;

import java.time.YearMonth;

import com.danielhw99.auth.security.AuthPrincipal;
import com.danielhw99.budget.service.BudgetService;
import com.danielhw99.common.api.ApiFormats;
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
        YearMonth parsed = ApiFormats.parseMonth(month);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.prepare(principal.userId(), parsed));
    }

    @GetMapping("/budget")
    public ResponseEntity<BudgetDtos.Summary> get(@AuthenticationPrincipal AuthPrincipal principal,
                                                   @PathVariable String month) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.get(principal.userId(), ApiFormats.parseMonth(month)));
    }

    @PutMapping("/budget-setup")
    public ResponseEntity<BudgetDtos.Summary> setup(@AuthenticationPrincipal AuthPrincipal principal,
                                                     @PathVariable String month,
                                                     @RequestBody BudgetDtos.SetupInput input) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.setup(principal.userId(), ApiFormats.parseMonth(month), input));
    }

    @PutMapping("/food-budget")
    public ResponseEntity<BudgetDtos.Summary> updateFood(@AuthenticationPrincipal AuthPrincipal principal,
                                                          @PathVariable String month,
                                                          @RequestBody BudgetDtos.Food input) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.updateFood(principal.userId(), ApiFormats.parseMonth(month), input));
    }

    @PostMapping("/budget-items")
    public ResponseEntity<BudgetDtos.Item> createItem(@AuthenticationPrincipal AuthPrincipal principal,
                                                       @PathVariable String month,
                                                       @RequestBody BudgetDtos.ItemInput input) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(service.createItem(principal.userId(), ApiFormats.parseMonth(month), input));
    }

    @PutMapping("/budget-items/{id}")
    public ResponseEntity<BudgetDtos.Item> updateItem(@AuthenticationPrincipal AuthPrincipal principal,
                                                       @PathVariable String month, @PathVariable String id,
                                                       @RequestBody BudgetDtos.ItemInput input) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.updateItem(principal.userId(), ApiFormats.parseMonth(month),
                        com.danielhw99.common.api.ApiId.parse(id).value(), input));
    }

    @DeleteMapping("/budget-items/{id}")
    public ResponseEntity<Void> deleteItem(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable String month, @PathVariable String id) {
        service.deleteItem(principal.userId(), ApiFormats.parseMonth(month),
                com.danielhw99.common.api.ApiId.parse(id).value());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
