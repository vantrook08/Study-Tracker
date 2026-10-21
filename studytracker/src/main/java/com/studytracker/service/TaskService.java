package com.studytracker.service;

import com.studytracker.model.CourseSummary;
import com.studytracker.model.Status;
import com.studytracker.model.Task;
import com.studytracker.model.TaskType;
import com.studytracker.storage.TaskRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * The "rules" of the app: what counts as overdue, what's coming up, how course stats are
 * calculated. It doesn't know or care whether it's being driven by a command line, a GUI,
 * or a test, which is exactly what makes it easy to test.
 */
public class TaskService {
    private static final Comparator<Task> SOONEST_FIRST =
            Comparator.comparing(Task::getDue).thenComparingInt(Task::getId);

    private final TaskRepository repository;
    // Asking a Clock for the time (instead of calling LocalDateTime.now() directly) lets tests
    // freeze time at a known moment, so "is this overdue?" has a predictable answer.
    private final Clock clock;

    public TaskService(TaskRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public Task add(String course, String title, TaskType type, LocalDateTime due) {
        Task task = new Task(repository.nextId(), matchExistingCourse(course), title, type, due);
        repository.save(task);
        return task;
    }

    public Task get(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("There's no task with id " + id + "."));
    }

    /**
     * Tasks sorted soonest-first.
     *
     * @param course      only this course, or null for every course
     * @param includeDone whether finished tasks should be included
     */
    public List<Task> list(String course, boolean includeDone) {
        return repository.findAll().stream()
                .filter(task -> includeDone || !task.isDone())
                .filter(task -> course == null || sameCourse(task.getCourse(), course))
                .sorted(SOONEST_FIRST)
                .toList();
    }

    /** Unfinished tasks due from now through the end of the day {@code days} days from today. */
    public List<Task> upcoming(int days) {
        if (days < 0) {
            throw new IllegalArgumentException("Days can't be negative.");
        }
        LocalDateTime now = now();
        LocalDateTime cutoff = now.toLocalDate().plusDays(days + 1L).atStartOfDay();
        return repository.findAll().stream()
                .filter(task -> !task.isDone())
                .filter(task -> !task.getDue().isBefore(now) && task.getDue().isBefore(cutoff))
                .sorted(SOONEST_FIRST)
                .toList();
    }

    public List<Task> overdue() {
        LocalDateTime now = now();
        return repository.findAll().stream()
                .filter(task -> task.isOverdue(now))
                .sorted(SOONEST_FIRST)
                .toList();
    }

    public Task setStatus(int id, Status status) {
        Task task = get(id);
        task.setStatus(status);
        repository.save(task);
        return task;
    }

    /** Records a grade (as a percent) and marks the task done, since graded means finished. */
    public Task recordScore(int id, double percent) {
        Task task = get(id);
        task.setScore(percent);
        task.setStatus(Status.DONE);
        repository.save(task);
        return task;
    }

    public Task delete(int id) {
        Task task = get(id);
        repository.delete(id);
        return task;
    }

    /** One summary per course, in alphabetical order. */
    public List<CourseSummary> summarizeByCourse() {
        LocalDateTime now = now();
        Map<String, List<Task>> byCourse = repository.findAll().stream()
                .collect(Collectors.groupingBy(task -> courseKey(task.getCourse()), TreeMap::new, Collectors.toList()));

        List<CourseSummary> summaries = new ArrayList<>();
        for (List<Task> tasks : byCourse.values()) {
            int done = (int) tasks.stream().filter(Task::isDone).count();
            int overdue = (int) tasks.stream().filter(task -> task.isOverdue(now)).count();
            OptionalDouble average = tasks.stream()
                    .map(Task::getScore)
                    .filter(OptionalDouble::isPresent)
                    .mapToDouble(OptionalDouble::getAsDouble)
                    .average();
            String displayName = tasks.get(0).getCourse();
            summaries.add(new CourseSummary(displayName, tasks.size() - done, done, overdue, average));
        }
        return summaries;
    }

    /** If you already have "CS 314" and type "cs314", reuse "CS 314" so the list stays consistent. */
    private String matchExistingCourse(String course) {
        if (course == null || course.isBlank()) {
            return course; // let Task's constructor reject it with a proper message
        }
        return repository.findAll().stream()
                .map(Task::getCourse)
                .filter(existing -> sameCourse(existing, course))
                .findFirst()
                .orElse(course);
    }

    /** "CS 314", "cs314", and "Cs  314" all count as the same course. */
    static boolean sameCourse(String a, String b) {
        return courseKey(a).equals(courseKey(b));
    }

    private static String courseKey(String course) {
        return course.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }
}
