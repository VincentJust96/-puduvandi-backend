package com.puduvandi.whatsapp.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Optional;

/**
 * Understands the dates customers actually type in chat:
 * "today", "tomorrow", "05-10-2026", "5/10/2026", optionally followed by a
 * 24-hour time ("05-10-2026 14:30", "tomorrow 09:00"). All parsing is
 * lenient about spacing and case, strict about real calendar dates.
 */
public final class DateInputParser {

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("d-M-uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT)
    };
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm");

    private DateInputParser() {}

    public static Optional<LocalDate> parseDate(String input, LocalDate today) {
        if (input == null) {
            return Optional.empty();
        }
        String text = input.trim().toLowerCase();
        if (text.equals("today")) {
            return Optional.of(today);
        }
        if (text.equals("tomorrow")) {
            return Optional.of(today.plusDays(1));
        }
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return Optional.of(LocalDate.parse(text, format));
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        return Optional.empty();
    }

    /** Date plus optional time; the time falls back to {@code defaultTime}. */
    public static Optional<LocalDateTime> parseDateTime(String input, LocalDate today, LocalTime defaultTime) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String[] parts = input.trim().split("\\s+", 2);
        Optional<LocalDate> date = parseDate(parts[0], today);
        if (date.isEmpty()) {
            return Optional.empty();
        }
        if (parts.length == 1) {
            return Optional.of(date.get().atTime(defaultTime));
        }
        try {
            return Optional.of(date.get().atTime(LocalTime.parse(parts[1].trim(), TIME_FORMAT)));
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    /** A plain positive whole number ("2", " 12 "), else empty. */
    public static Optional<Integer> parseQuantity(String input) {
        if (input == null || !input.trim().matches("\\d{1,3}")) {
            return Optional.empty();
        }
        int value = Integer.parseInt(input.trim());
        return value > 0 ? Optional.of(value) : Optional.empty();
    }
}
