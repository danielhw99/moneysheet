package com.danielhw99.memo.api;
import java.time.LocalDate;
import java.time.LocalTime;
import com.danielhw99.common.api.ApiId;
import io.swagger.v3.oas.annotations.media.Schema;
@Schema(requiredProperties={"id","date","time","title","amount","direction","completedOn"})
public record MemoResponse(ApiId id, LocalDate date, LocalTime time, String title, @Schema(minimum="1",maximum="9999999999") long amount,
        MemoDirectionValue direction, @Schema(nullable=true,requiredMode=Schema.RequiredMode.REQUIRED) LocalDate completedOn) {}
