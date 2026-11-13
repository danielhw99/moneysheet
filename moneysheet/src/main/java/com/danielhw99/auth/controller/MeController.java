package com.danielhw99.auth.controller;
import com.danielhw99.auth.dto.Profile;
import com.danielhw99.auth.security.AuthPrincipal;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import com.danielhw99.user.repository.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
@RestController
public class MeController {
    private final UserRepository users;
    public MeController(UserRepository users) { this.users=users; }
    @GetMapping("/api/v1/me")
    @Operation(summary="현재 사용자 조회")
    public Profile me(@AuthenticationPrincipal AuthPrincipal principal) {
        return users.findById(principal.userId()).map(Profile::from)
                .orElseThrow(()->new ApiException(ErrorCode.UNAUTHENTICATED));
    }
}
