package com.danielhw99.auth.controller;

import com.danielhw99.auth.dto.CsrfResponse;
import com.danielhw99.auth.dto.Profile;
import com.danielhw99.auth.dto.SignupInput;
import com.danielhw99.auth.dto.LoginInput;
import com.danielhw99.auth.service.LoginService;
import com.danielhw99.auth.security.SessionExpiryFilter;
import java.time.Clock;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
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
import io.swagger.v3.oas.annotations.Parameter;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final SignupService signup;
    private final LoginService login;
    private final HttpSessionSecurityContextRepository contexts;
    private final HttpSessionCsrfTokenRepository csrfTokens;
    private final Clock clock;
    private final boolean secureCookie;
    public AuthController(SignupService signup, LoginService login, HttpSessionSecurityContextRepository contexts,
            HttpSessionCsrfTokenRepository csrfTokens, Clock clock,
            @Value("${server.servlet.session.cookie.secure:true}") boolean secureCookie) {
        this.signup=signup;this.login=login;this.contexts=contexts;
        this.csrfTokens=csrfTokens;this.clock=clock;this.secureCookie=secureCookie;
    }

    @GetMapping("/csrf")
    @Operation(summary="CSRF 토큰 취득", description="가입 및 로그인 전 취득하고 로그인·로그아웃 후 다시 취득합니다.")
    public CsrfResponse csrf(@Parameter(hidden = true) CsrfToken token) { return new CsrfResponse(token.getToken(), token.getHeaderName()); }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary="회원가입", description="성공 후 자동 로그인하지 않습니다. 개인정보처리방침 버전 기본값은 v1입니다.")
    public Profile signup(@Valid @RequestBody SignupInput input) { return signup.signup(input); }

    @PostMapping("/login")
    @Operation(summary="로그인", description="세션 ID 교체 후 프로필 반환. CSRF 토큰을 다시 취득해야 합니다.")
    public Profile login(@Valid @RequestBody LoginInput input, HttpServletRequest request,HttpServletResponse response) {
        var principal=login.authenticate(input);
        var session=request.getSession();
        request.changeSessionId();
        csrfTokens.saveToken(null,request,response);
        session.setAttribute(SessionExpiryFilter.LOGIN_AT,clock.instant());
        session.setAttribute(SessionExpiryFilter.LAST_SEEN,clock.instant());
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,List.of()));
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context,request,response);
        return principal.profile();
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary="로그아웃", description="현재 세션을 무효화합니다. 유효한 CSRF 토큰이 필요합니다.")
    public void logout(HttpServletRequest request,HttpServletResponse response) {
        new SecurityContextLogoutHandler().logout(request,response,SecurityContextHolder.getContext().getAuthentication());
        response.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from("JSESSIONID", "").path("/")
                .httpOnly(true).secure(secureCookie).sameSite("Lax").maxAge(0).build().toString());
    }
}
