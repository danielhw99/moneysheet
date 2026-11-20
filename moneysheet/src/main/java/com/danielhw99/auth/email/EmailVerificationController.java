package com.danielhw99.auth.email;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/email-verifications")
public class EmailVerificationController {
    public record Request(
        @NotBlank @Email @Pattern(regexp="^[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+$") @Size(max=254) String email) {
        @Override public String toString(){return "Request[REDACTED]";}
    }
    public record Confirm(
        @NotBlank @Email @Pattern(regexp="^[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+$") @Size(max=254) String email,
        @NotBlank @Pattern(regexp="[0-9a-f-]{36}") String challengeId,
        @NotBlank @Pattern(regexp="[0-9]{6}") @Schema(accessMode=Schema.AccessMode.WRITE_ONLY) String code) {
        @Override public String toString(){return "Confirm[REDACTED]";}
    }
    private final EmailVerificationService service;
    public EmailVerificationController(EmailVerificationService service){this.service=service;}
    @PostMapping
    @Operation(summary="가입 이메일 인증번호 발송",description="6자리/10분. 재발송60초, 이메일·IP당 시간당10회. 재발송은 이전 번호와 가입 증명을 폐기합니다. SMTP 실패는500입니다.")
    public EmailVerificationService.Sent request(@Valid @RequestBody Request input){return service.request(input.email());}
    @PostMapping("/confirmation")
    @Operation(summary="이메일 인증번호 확인",description="오답5회 제한. 성공 증명은10분 유효하며 동일 이메일의 가입에서 한 번만 사용합니다.")
    public EmailVerificationService.Verified confirm(@Valid @RequestBody Confirm input){return service.confirm(input.email(),input.challengeId(),input.code());}
}
