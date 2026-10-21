package com.studytracker.model;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * One thing you have to turn in or show up for: an assignment, quiz, exam, or project.
 *
 * <p>The identifying details (course, title, type, due date) are final. Status and score
 * change over time, so they have setters. The constructor validates everything, which means
 * a Task object can never exist in a broken state (blank title, negative score, etc).
 */
public class Task {
    private final int id;
    private final String course;
    private final String title;
    private final TaskType type;
    private final LocalDateTime due;
    private Status status;
    private Double score; // percent, null until graded

    /** Creates a brand-new, not-started task. */
    public Task(int id, String course, String title, TaskType type, LocalDateTime due) {
        this(id, course, title, type, due, Status.TODO, null);
    }

    /** Full constructor, used when loading saved tasks. {@code score} may be null. */
    public Task(int id, String course, String title, TaskType type, LocalDateTime due,
                Status status, Double score) {
        if (id <= 0) {
            throw new IllegalArgumentException("Task id must be positive, got " + id + ".");
        }
        this.id = id;
        this.course = normalizeCourse(course);
        this.title = cleanText(title, "Title");
        this.type = Objects.requireNonNull(type, "type");
        this.due = Objects.requireNonNull(due, "due");
        this.status = Objects.requireNonNull(status, "status");
        this.score = score == null ? null : checkScore(score);
    }

    public int getId() {
        return id;
    }

    public String getCourse() {
        return course;
    }

    public String getTitle() {
        return title;
    }

    public TaskType getType() {
        return type;
    }

    public LocalDateTime getDue() {
        return due;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    /** The grade as a percent, if one has been recorded. */
    public OptionalDouble getScore() {
        return score == null ? OptionalDouble.empty() : OptionalDouble.of(score);
    }

    /** Records a grade as a percent. Over 100 is allowed (extra credit happens). */
    public void setScore(double percent) {
        this.score = checkScore(percent);
    }

    public boolean isDone() {
        return status == Status.DONE;
    }

    /** True if this isn't finished and its due date has already passed. */
    public boolean isOverdue(LocalDateTime now) {
        return !isDone() && due.isBefore(now);
    }

    @Override
    public String toString() {
        return "Task{id=" + id + ", course=" + course + ", title=" + title + ", type=" + type
                + ", due=" + due + ", status=" + status + ", score=" + score + "}";
    }

    // These helpers are static on purpose: a constructor shouldn't call methods that a subclass
    // could override, because the subclass's version would run before the object is fully built.
    private static double checkScore(double percent) {
        if (!Double.isFinite(percent) || percent < 0) {
            throw new IllegalArgumentException("Score must be a number 0 or higher.");
        }
        return percent;
    }

    /** "  cs   314 " becomes "CS 314" so the same course isn't stored three different ways. */
    private static String normalizeCourse(String course) {
        return cleanText(course, "Course").replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    /** Rejects blank text and flattens line breaks (they'd break the one-task-per-line save file). */
    private static String cleanText(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " can't be blank.");
        }
        return text.strip().replaceAll("\\s*\\R\\s*", " ");
    }
}
