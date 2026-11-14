package com.danielhw99.memo.api;
import java.time.LocalDate;
import java.time.LocalTime;
import com.danielhw99.common.api.ApiId;
import io.swagger.v3.oas.annotations.media.Schema;
public record MemoResponse(ApiId id, LocalDate date, LocalTime time, String title, long amount,
        MemoDirectionValue direction, @Schema(nullable=true,requiredMode=Schema.RequiredMode.REQUIRED) LocalDate completedOn) {}
