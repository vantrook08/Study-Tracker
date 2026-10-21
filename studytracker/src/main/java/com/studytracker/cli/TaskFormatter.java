package com.studytracker.cli;

import com.studytracker.model.CourseSummary;
import com.studytracker.model.Status;
import com.studytracker.model.Task;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/** Everything about how tasks look when printed. Kept separate so the CLI class stays readable. */
final class TaskFormatter {
    private static final DateTimeFormatter DUE_FORMAT =
            DateTimeFormatter.ofPattern("EEE MMM d, h:mm a", Locale.US);
    private static final String ROW = "%4s  %-10s  %-9s  %-28s  %-20s  %s%n";

    private TaskFormatter() {
    }

    static String table(List<Task> tasks, LocalDateTime now) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(ROW, "ID", "TYPE", "COURSE", "TITLE", "DUE", "STATUS"));
        for (Task task : tasks) {
            sb.append(String.format(ROW,
                    task.getId(),
                    task.getType().label(),
                    truncate(task.getCourse(), 9),
                    truncate(task.getTitle(), 28),
                    formatDue(task.getDue()),
                    status(task, now)));
        }
        return sb.toString();
    }

    static String courseTable(List<CourseSummary> summaries) {
        String row = "%-10s  %5s  %5s  %8s  %9s%n";
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(row, "COURSE", "OPEN", "DONE", "OVERDUE", "AVG SCORE"));
        for (CourseSummary s : summaries) {
            String average = s.averageScore().isPresent() ? percent(s.averageScore().getAsDouble()) + "%" : "-";
            sb.append(String.format(row, truncate(s.course(), 10), s.open(), s.done(), s.overdue(), average));
        }
        return sb.toString();
    }

    /** One-line description, e.g. "Exam 'Midterm 2' for CS 314, due Thu Oct 29, 7:00 PM (in 9 days)". */
    static String describe(Task task, LocalDateTime now) {
        return task.getType().label() + " '" + task.getTitle() + "' for " + task.getCourse()
                + ", due " + formatDue(task.getDue()) + " (" + relativeDue(task.getDue(), now) + ")";
    }

    static String formatDue(LocalDateTime due) {
        return due.format(DUE_FORMAT);
    }

    static String status(Task task, LocalDateTime now) {
        if (task.isDone()) {
            return task.getScore().isPresent() ? "done (" + percent(task.getScore().getAsDouble()) + "%)" : "done";
        }
        String when = relativeDue(task.getDue(), now);
        return task.getStatus() == Status.IN_PROGRESS ? when + ", in progress" : when;
    }

    static String relativeDue(LocalDateTime due, LocalDateTime now) {
        if (due.isBefore(now)) {
            long daysLate = ChronoUnit.DAYS.between(due.toLocalDate(), now.toLocalDate());
            if (daysLate == 0) {
                return "OVERDUE (today)";
            }
            return "OVERDUE by " + daysLate + (daysLate == 1 ? " day" : " days");
        }
        long daysAway = ChronoUnit.DAYS.between(now.toLocalDate(), due.toLocalDate());
        if (daysAway == 0) {
            long hours = Duration.between(now, due).toHours();
            return hours < 1 ? "due within the hour" : "today, " + hours + "h left";
        }
        if (daysAway == 1) {
            return "tomorrow";
        }
        return "in " + daysAway + " days";
    }

    /** 92.0 -> "92", 91.666 -> "91.7" */
    static String percent(double value) {
        double rounded = Math.round(value * 10) / 10.0;
        return rounded == Math.rint(rounded)
                ? String.format(Locale.US, "%.0f", rounded)
                : String.format(Locale.US, "%.1f", rounded);
    }

    static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }
}
