package com.danielhw99.user.api;
import com.danielhw99.auth.controller.AuthController;
import com.danielhw99.auth.dto.Profile;
import com.danielhw99.auth.security.AuthPrincipal;
import com.danielhw99.user.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
@RestController
@RequestMapping("/api/v1/me")
public class AccountController {
 private final AccountService service;
 private final AuthController authentication;
 public AccountController(AccountService service,AuthController authentication){this.service=service;this.authentication=authentication;}
 @PatchMapping @Operation(summary="닉네임 변경",description="세션을 유지하고 변경된 프로필을 반환합니다. 이메일 변경은 지원하지 않습니다.")
 public Profile rename(@AuthenticationPrincipal AuthPrincipal principal,@Valid @RequestBody AccountInputs.Nickname input){return service.updateNickname(principal.userId(),input.nickname());}
 @PutMapping("/password") @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(summary="비밀번호 변경",description="현재 비밀번호 확인 후 모든 본인 세션을 폐기합니다. 재로그인해야 합니다.")
 public void password(@AuthenticationPrincipal AuthPrincipal principal,@Valid @RequestBody AccountInputs.Password input,HttpServletRequest request,HttpServletResponse response){service.changePassword(principal.userId(),input.currentPassword(),input.newPassword());authentication.logout(request,response);}
 @PostMapping("/deletion") @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(summary="회원 탈퇴",description="현재 비밀번호 확인 후 계정 및 개인 업무 데이터를 즉시 삭제합니다. 복구할 수 없습니다.")
 public void delete(@AuthenticationPrincipal AuthPrincipal principal,@Valid @RequestBody AccountInputs.Deletion input,HttpServletRequest request,HttpServletResponse response){service.deleteAccount(principal.userId(),input.currentPassword());authentication.logout(request,response);}
}
