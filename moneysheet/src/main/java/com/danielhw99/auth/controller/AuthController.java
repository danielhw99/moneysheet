package com.danielhw99.auth.controller;

import com.danielhw99.auth.dto.CsrfResponse;
import com.danielhw99.auth.dto.Profile;
import com.danielhw99.auth.dto.SignupInput;
import com.danielhw99.auth.service.SignupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final SignupService signup;
    public AuthController(SignupService signup) { this.signup=signup; }

    @GetMapping("/csrf")
    @Operation(summary="CSRF 토큰 취득", description="가입 및 로그인 전 취득하고 로그인·로그아웃 후 다시 취득합니다.")
    public CsrfResponse csrf(CsrfToken token) { return new CsrfResponse(token.getToken(), token.getHeaderName()); }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary="회원가입", description="성공 후 자동 로그인하지 않습니다. 개인정보처리방침 버전 기본값은 v1입니다.")
    public Profile signup(@Valid @RequestBody SignupInput input) { return signup.signup(input); }
}
