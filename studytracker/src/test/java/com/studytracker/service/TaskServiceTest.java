package com.studytracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.studytracker.model.CourseSummary;
import com.studytracker.model.Status;
import com.studytracker.model.Task;
import com.studytracker.model.TaskType;
import com.studytracker.storage.InMemoryTaskRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskServiceTest {
    // Freeze time at Tuesday Oct 20, 2026, 10:00 AM so overdue/upcoming are predictable.
    private static final ZoneId ZONE = ZoneId.of("America/Chicago");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 10, 0);

    private TaskService service;

    @BeforeEach
    void setUp() {
        Clock frozen = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        service = new TaskService(new InMemoryTaskRepository(), frozen);
    }

    private Task add(String course, String title, LocalDateTime due) {
        return service.add(course, title, TaskType.ASSIGNMENT, due);
    }

    private static List<String> titles(List<Task> tasks) {
        return tasks.stream().map(Task::getTitle).toList();
    }

    @Test
    void listShowsSoonestFirst() {
        add("CS 314", "Later", NOW.plusDays(5));
        add("CS 314", "Sooner", NOW.plusDays(1));

        assertEquals(List.of("Sooner", "Later"), titles(service.list(null, false)));
    }

    @Test
    void finishedTasksAreHiddenUnlessAskedFor() {
        Task finished = add("CS 314", "Finished", NOW.plusDays(1));
        add("CS 314", "Open", NOW.plusDays(2));
        service.setStatus(finished.getId(), Status.DONE);

        assertEquals(List.of("Open"), titles(service.list(null, false)));
        assertEquals(List.of("Finished", "Open"), titles(service.list(null, true)));
    }

    @Test
    void courseFilterIgnoresCaseAndSpacing() {
        add("CS 314", "Data structures hw", NOW.plusDays(1));
        add("M 408D", "Calc hw", NOW.plusDays(1));

        assertEquals(List.of("Data structures hw"), titles(service.list("cs314", false)));
    }

    @Test
    void addingWithADifferentSpellingReusesTheExistingCourseName() {
        add("CS 314", "First", NOW.plusDays(1));
        Task second = add("cs314", "Second", NOW.plusDays(2));

        assertEquals("CS 314", second.getCourse());
    }

    @Test
    void overdueMeansPastDueAndNotFinished() {
        add("CS 314", "Late", NOW.minusDays(1));
        Task lateButDone = add("CS 314", "Late but done", NOW.minusDays(2));
        add("CS 314", "Not due yet", NOW.plusHours(1));
        service.setStatus(lateButDone.getId(), Status.DONE);

        assertEquals(List.of("Late"), titles(service.overdue()));
    }

    @Test
    void upcomingRunsThroughTheEndOfTheLastDay() {
        add("CS 314", "Last minute of day 7", NOW.plusDays(7).withHour(23).withMinute(59));
        add("CS 314", "Just after day 7", NOW.plusDays(8).withHour(0).withMinute(1));
        add("CS 314", "Already overdue", NOW.minusHours(1));

        assertEquals(List.of("Last minute of day 7"), titles(service.upcoming(7)));
    }

    @Test
    void recordingAScoreFinishesTheTaskAndFeedsTheCourseAverage() {
        Task a = add("CS 314", "A1", NOW.minusDays(3));
        Task b = add("CS 314", "A2", NOW.minusDays(1));
        add("M 408D", "Calc", NOW.plusDays(1));

        service.recordScore(a.getId(), 90);
        service.recordScore(b.getId(), 100);

        List<CourseSummary> summaries = service.summarizeByCourse();
        assertEquals(2, summaries.size());
        CourseSummary cs = summaries.get(0);
        assertEquals("CS 314", cs.course());
        assertEquals(2, cs.done());
        assertEquals(0, cs.overdue()); // both were late, but they're done now
        assertEquals(95.0, cs.averageScore().getAsDouble(), 1e-9);
        assertTrue(summaries.get(1).averageScore().isEmpty());
    }

    @Test
    void deleteRemovesTheTask() {
        Task task = add("CS 314", "Oops", NOW.plusDays(1));
        service.delete(task.getId());

        assertTrue(service.list(null, true).isEmpty());
    }

    @Test
    void unknownIdsGiveAHelpfulError() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.setStatus(42, Status.DONE));
        assertTrue(e.getMessage().contains("42"));
    }

    @Test
    void invalidInputIsRejectedBeforeAnythingIsSaved() {
        assertThrows(IllegalArgumentException.class, () -> add("  ", "No course", NOW));
        assertThrows(IllegalArgumentException.class, () -> add("CS 314", "", NOW));
        assertTrue(service.list(null, true).isEmpty());
    }
}
