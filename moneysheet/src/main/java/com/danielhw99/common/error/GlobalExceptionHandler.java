package com.danielhw99.common.error;

import java.util.List;

import com.danielhw99.common.web.TraceIdFilter;

import jakarta.servlet.http.HttpServletRequest;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApiException(ApiException exception, HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        if (exception.getRetryAfterSeconds() != null) {
            headers.set(HttpHeaders.RETRY_AFTER, exception.getRetryAfterSeconds().toString());
        }
        return errorResponse(exception.getErrorCode(), exception.getErrorCode().getStatus(), headers,
                List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpectedException(Exception exception, HttpServletRequest request) {
        logUnexpected(exception, request);
        return errorResponse(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getStatus(),
                HttpHeaders.EMPTY, List.of(), request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception exception, @Nullable Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest webRequest) {
        ServletWebRequest servletRequest = (ServletWebRequest) webRequest;
        if (servletRequest.getResponse() != null && servletRequest.getResponse().isCommitted()) {
            return null;
        }
        HttpServletRequest request = servletRequest.getRequest();
        if (status.is5xxServerError()) {
            logUnexpected(exception, request);
        }
        return errorResponse(errorCodeFor(status), status, headers, fieldErrors(exception), request);
    }

    private ResponseEntity<Object> errorResponse(ErrorCode code, HttpStatusCode status, HttpHeaders headers,
            List<ApiFieldError> fieldErrors, HttpServletRequest request) {
        String traceId = TraceIdFilter.getOrCreateTraceId(request);
        ApiError error = new ApiError(code.name(), code.getMessage(), fieldErrors, traceId);
        return ResponseEntity.status(status).headers(headers).contentType(MediaType.APPLICATION_JSON)
                .header(TraceIdFilter.TRACE_HEADER, traceId).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(error);
    }

    private ErrorCode errorCodeFor(HttpStatusCode status) {
        if (status.is5xxServerError()) {
            return ErrorCode.INTERNAL_ERROR;
        }
        return switch (status.value()) {
            case 401 -> ErrorCode.UNAUTHENTICATED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 409 -> ErrorCode.CONFLICT;
            case 429 -> ErrorCode.RATE_LIMITED;
            default -> ErrorCode.VALIDATION;
        };
    }

    private List<ApiFieldError> fieldErrors(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException invalid) {
            return invalid.getBindingResult().getFieldErrors().stream()
                    .map(error -> new ApiFieldError(error.getField(), "입력값을 확인해주세요."))
                    .distinct().toList();
        }
        if (exception instanceof HandlerMethodValidationException invalid && !invalid.isForReturnValue()) {
            return invalid.getParameterValidationResults().stream().map(result -> {
                String name = result.getMethodParameter().getParameterName();
                return new ApiFieldError(name == null ? "arg" + result.getMethodParameter().getParameterIndex()
                        : name, "입력값을 확인해주세요.");
            }).distinct().toList();
        }
        if (exception instanceof MethodArgumentTypeMismatchException mismatch) {
            return List.of(new ApiFieldError(mismatch.getName(), "값의 형식을 확인해주세요."));
        }
        if (exception instanceof MissingServletRequestParameterException missing) {
            return List.of(new ApiFieldError(missing.getParameterName(), "필수 입력값입니다."));
        }
        return List.of();
    }

    private void logUnexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unhandled API exception: traceId={}, type={}",
                TraceIdFilter.getOrCreateTraceId(request), exception.getClass().getSimpleName());
    }
}
