package com.danielhw99.balance.api;
import java.time.LocalDate;
import com.danielhw99.auth.security.AuthPrincipal;
import com.danielhw99.balance.service.DailyBalanceCheckService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
@RestController @RequestMapping("/api/v1/daily-balance-checks")
public class DailyBalanceCheckController {
 public record Input(@Min(0) @Max(9999999999L) Long actualBalance,@NotNull Boolean locked) {}
 private final DailyBalanceCheckService service;
 public DailyBalanceCheckController(DailyBalanceCheckService service) {this.service=service;}
 @GetMapping("/{date}") public ResponseEntity<DailyBalanceCheckService.Result> get(@AuthenticationPrincipal AuthPrincipal user,@PathVariable LocalDate date) {
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(user.userId(),date));
 }
 @PutMapping("/{date}") public ResponseEntity<DailyBalanceCheckService.Result> save(@AuthenticationPrincipal AuthPrincipal user,@PathVariable LocalDate date,@Valid @RequestBody Input input) {
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.save(user.userId(),date,input.actualBalance(),input.locked()));
 }
}
