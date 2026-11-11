package com.danielhw99.common.api;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

public final class ApiFormats {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("uuuu-MM")
            .withResolverStyle(ResolverStyle.STRICT);

    private ApiFormats() {
    }

    public static LocalDate parseDate(String value) {
        requirePattern(value, "[0-9]{4}-[0-9]{2}-[0-9]{2}");
        return LocalDate.parse(value, DATE);
    }

    public static LocalTime parseTime(String value) {
        requirePattern(value, "([01][0-9]|2[0-3]):[0-5][0-9]");
        return LocalTime.parse(value, TIME);
    }

    public static YearMonth parseMonth(String value) {
        requirePattern(value, "[0-9]{4}-[0-9]{2}");
        return YearMonth.parse(value, MONTH);
    }

    public static String formatDate(LocalDate value) {
        String formatted = value.format(DATE);
        requirePattern(formatted, "[0-9]{4}-[0-9]{2}-[0-9]{2}");
        return formatted;
    }

    public static String formatTime(LocalTime value) {
        if (value.getSecond() != 0 || value.getNano() != 0) {
            throw new IllegalArgumentException("API time must have minute precision.");
        }
        return value.format(TIME);
    }

    public static String formatMonth(YearMonth value) {
        String formatted = value.format(MONTH);
        requirePattern(formatted, "[0-9]{4}-[0-9]{2}");
        return formatted;
    }

    private static void requirePattern(String value, String pattern) {
        if (value == null || !value.matches(pattern)) {
            throw new IllegalArgumentException("Invalid API date/time format.");
        }
    }
}
