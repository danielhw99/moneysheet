package com.danielhw99.memo.api;
import java.time.LocalDate;
import jakarta.validation.constraints.NotNull;
public record MemoCompletionInput(@NotNull LocalDate completedOn) {}
