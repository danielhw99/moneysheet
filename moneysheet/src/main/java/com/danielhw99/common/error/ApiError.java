package com.danielhw99.common.error;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공통 오류 응답. 입력값·SQL·내부 예외 정보는 포함하지 않습니다.")
public record ApiError(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "VALIDATION") String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "입력값을 확인해주세요.") String message,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "필드 오류. 해당 없으면 빈 배열")
        List<ApiFieldError> fieldErrors,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "서버가 생성한 요청 추적 ID") String traceId) {

    public ApiError {
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }
}
