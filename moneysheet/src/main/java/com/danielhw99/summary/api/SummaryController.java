package com.danielhw99.summary.api;

import java.time.LocalDate;
import java.time.YearMonth;
import com.danielhw99.auth.security.AuthPrincipal;
import com.danielhw99.summary.service.SummaryService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;

@RestController
@RequestMapping("/api/v1/summaries")
public class SummaryController {
    private final SummaryService service;
    public SummaryController(SummaryService service) { this.service=service; }
    @GetMapping("/daily")
    public ResponseEntity<SummaryDtos.Daily> daily(@AuthenticationPrincipal AuthPrincipal user,@RequestParam LocalDate date) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.daily(user.userId(),date));
    }
    @GetMapping("/monthly")
    public ResponseEntity<SummaryDtos.Monthly> monthly(@AuthenticationPrincipal AuthPrincipal user,
            @Parameter(schema=@Schema(type="string",pattern="^[0-9]{4}-(0[1-9]|1[0-2])$")) @RequestParam YearMonth month) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.monthly(user.userId(),month));
    }
    @GetMapping("/home")
    public ResponseEntity<SummaryDtos.Home> home(@AuthenticationPrincipal AuthPrincipal user,
            @Parameter(schema=@Schema(type="string",pattern="^[0-9]{4}-(0[1-9]|1[0-2])$")) @RequestParam YearMonth month) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.home(user.userId(),month));
    }
}
