package com.danielhw99.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION(HttpStatus.BAD_REQUEST, "입력값을 확인해주세요."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호를 확인해주세요."),
    CSRF_INVALID(HttpStatus.FORBIDDEN, "요청 인증 정보를 확인해주세요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 정보를 찾을 수 없습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    BUDGET_READ_ONLY(HttpStatus.CONFLICT, "지난달 예산은 수정할 수 없습니다."),
    FUTURE_MONTH_NOT_ALLOWED(HttpStatus.CONFLICT, "미래 월 예산은 준비할 수 없습니다."),
    BUDGET_NOT_READY(HttpStatus.CONFLICT, "월 예산 설정을 먼저 완료해주세요."),
    PAYMENT_EXCEEDS_BUDGET(HttpStatus.CONFLICT, "지급액이 남은 예산을 초과합니다."),
    BUDGET_BELOW_PAID(HttpStatus.CONFLICT, "예산은 지급된 금액보다 작을 수 없습니다."),
    REFERENCED(HttpStatus.CONFLICT, "다른 기록에서 사용 중인 항목입니다."),
    // Reserved legacy error code; no active policy forbids ordinary fixed payments.
    ALLOWANCE_PAYMENT_FORBIDDEN(HttpStatus.CONFLICT, "고정여유비는 수동 지급할 수 없습니다."),
    AMOUNT_RANGE_EXCEEDED(HttpStatus.CONFLICT, "계산 금액이 허용 범위를 초과했습니다."),
    CONFLICT(HttpStatus.CONFLICT, "요청이 현재 상태와 충돌합니다."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "요청이 많습니다. 잠시 후 다시 시도해주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "요청 처리 중 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
