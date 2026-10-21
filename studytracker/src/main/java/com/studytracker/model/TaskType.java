package com.studytracker.model;

import java.util.Locale;

/** The kinds of things a student has to keep track of. */
public enum TaskType {
    ASSIGNMENT("Assignment"),
    QUIZ("Quiz"),
    EXAM("Exam"),
    PROJECT("Project");

    private final String label;

    TaskType(String label) {
        this.label = label;
    }

    /** Human-friendly name, e.g. "Exam" instead of "EXAM". */
    public String label() {
        return label;
    }

    /**
     * Turns user input like "exam", "Exam", "hw", or just "e" into a TaskType.
     * Every type starts with a different letter, so a one-letter prefix is never ambiguous.
     */
    public static TaskType parse(String text) {
        String input = text == null ? "" : text.trim().toUpperCase(Locale.ROOT);
        if (input.equals("HW") || input.equals("HOMEWORK")) {
            return ASSIGNMENT;
        }
        if (!input.isEmpty()) {
            for (TaskType type : values()) {
                if (type.name().startsWith(input)) {
                    return type;
                }
            }
        }
        throw new IllegalArgumentException(
                "Unknown type '" + text + "'. Use assignment, quiz, exam, or project (or just a, q, e, p).");
    }
}
