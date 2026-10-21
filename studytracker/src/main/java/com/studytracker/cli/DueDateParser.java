package com.studytracker.cli;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns what a person would actually type into a date and time.
 *
 * <p>Date part (required): {@code today}, {@code tomorrow}, a weekday ({@code fri},
 * {@code friday}), {@code 10/27}, {@code 10/27/26}, or {@code 2026-10-27}.
 * <br>Time part (optional): {@code 2pm}, {@code 11:59pm}, {@code 14:00}, {@code noon}.
 * Leaving off the time means 11:59 PM, because that's when most things are due.
 */
public final class DueDateParser {
    public static final LocalTime DEFAULT_TIME = LocalTime.of(23, 59);

    private static final Pattern ISO_DATE = Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})");
    private static final Pattern US_DATE = Pattern.compile("(\\d{1,2})/(\\d{1,2})(?:/(\\d{2}|\\d{4}))?");
    private static final Pattern TIME = Pattern.compile("(\\d{1,2})(?::(\\d{2}))?([ap]m?)?");

    private DueDateParser() {
    }

    public static LocalDateTime parse(String input, LocalDate today) {
        String text = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            throw new IllegalArgumentException("A due date is required.");
        }
        String[] parts = text.split("\\s+", 2);
        try {
            LocalDate date = parseDate(parts[0], today);
            LocalTime time = DEFAULT_TIME;
            if (parts.length == 2) {
                String timeText = parts[1].replaceFirst("^at\\s+", "").replace(" ", ""); // "at 2 pm" -> "2pm"
                time = parseTime(timeText);
            }
            return LocalDateTime.of(date, time);
        } catch (DateTimeException e) {
            // Thrown by Java for things like 2/30 or 13/1. Swap in a friendlier message.
            throw new IllegalArgumentException("'" + input.trim() + "' isn't a real date.");
        }
    }

    static LocalDate parseDate(String token, LocalDate today) {
        if (token.equals("today") || token.equals("tonight")) {
            return today;
        }
        if (token.equals("tomorrow") || token.equals("tmrw") || token.equals("tmr")) {
            return today.plusDays(1);
        }

        DayOfWeek weekday = parseWeekday(token);
        if (weekday != null) {
            // "fri" means the next Friday. If today is Friday, that's a week from now; use "today" for today.
            return today.with(TemporalAdjusters.next(weekday));
        }

        Matcher iso = ISO_DATE.matcher(token);
        if (iso.matches()) {
            return LocalDate.of(toInt(iso.group(1)), toInt(iso.group(2)), toInt(iso.group(3)));
        }

        Matcher us = US_DATE.matcher(token);
        if (us.matches()) {
            int month = toInt(us.group(1));
            int day = toInt(us.group(2));
            if (us.group(3) != null) {
                int year = toInt(us.group(3));
                return LocalDate.of(year < 100 ? 2000 + year : year, month, day);
            }
            LocalDate guess = LocalDate.of(today.getYear(), month, day);
            // No year given. If that date was months ago, they almost certainly mean next year
            // (typing "1/15" in December). A date a few weeks back is probably just a late entry.
            return guess.isBefore(today.minusMonths(2)) ? guess.plusYears(1) : guess;
        }

        throw new IllegalArgumentException(
                "Couldn't understand the date '" + token + "'. Try fri, tomorrow, 10/27, or 2026-10-27.");
    }

    static LocalTime parseTime(String token) {
        if (token.equals("noon")) {
            return LocalTime.NOON;
        }
        Matcher m = TIME.matcher(token);
        if (!m.matches()) {
            throw new IllegalArgumentException(
                    "Couldn't understand the time '" + token + "'. Try 2pm, 11:59pm, or 14:00.");
        }
        int hour = toInt(m.group(1));
        int minute = m.group(2) == null ? 0 : toInt(m.group(2));
        String amPm = m.group(3);

        if (amPm != null) {
            if (hour < 1 || hour > 12) {
                throw new IllegalArgumentException("With am/pm the hour has to be 1 to 12.");
            }
            boolean pm = amPm.startsWith("p");
            if (pm && hour != 12) {
                hour += 12;           // 2pm -> 14
            } else if (!pm && hour == 12) {
                hour = 0;             // 12am -> 00
            }
        } else if (m.group(2) == null) {
            // A bare "2" could mean 2am or 2pm. Guessing wrong means missing a deadline, so ask.
            throw new IllegalArgumentException("Is '" + token + "' am or pm? Write it like 2pm or 14:00.");
        }

        if (hour > 23 || minute > 59) {
            throw new IllegalArgumentException("'" + token + "' isn't a valid time.");
        }
        return LocalTime.of(hour, minute);
    }

    /** Accepts any prefix of a weekday name that's at least 3 letters: "thu", "thurs", "thursday". */
    private static DayOfWeek parseWeekday(String token) {
        if (token.length() < 3) {
            return null;
        }
        for (DayOfWeek day : DayOfWeek.values()) {
            if (day.name().toLowerCase(Locale.ROOT).startsWith(token)) {
                return day;
            }
        }
        return null;
    }

    private static int toInt(String digits) {
        return Integer.parseInt(digits);
    }
}
