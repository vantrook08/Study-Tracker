package com.studytracker.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.studytracker.model.Status;
import com.studytracker.model.Task;
import com.studytracker.model.TaskType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvTaskRepositoryTest {
    private static final LocalDateTime DUE = LocalDateTime.of(2026, 10, 27, 23, 59);

    // JUnit creates a fresh empty folder for each test and deletes it afterward.
    @TempDir
    Path tempDir;

    private Path file() {
        return tempDir.resolve("tasks.csv");
    }

    @Test
    void tasksSurviveARestartIncludingCommasAndQuotes() {
        CsvTaskRepository repo = new CsvTaskRepository(file());
        Task original = new Task(repo.nextId(), "CS 314", "Read ch. 3, then do \"challenge\" problems",
                TaskType.ASSIGNMENT, DUE);
        original.setScore(87.5);
        original.setStatus(Status.DONE);
        repo.save(original);

        Task loaded = new CsvTaskRepository(file()).findById(1).orElseThrow();

        assertEquals(original.getTitle(), loaded.getTitle());
        assertEquals("CS 314", loaded.getCourse());
        assertEquals(TaskType.ASSIGNMENT, loaded.getType());
        assertEquals(DUE, loaded.getDue());
        assertEquals(Status.DONE, loaded.getStatus());
        assertEquals(87.5, loaded.getScore().getAsDouble(), 1e-9);
    }

    @Test
    void idsKeepCountingUpAfterARestart() {
        CsvTaskRepository repo = new CsvTaskRepository(file());
        repo.save(new Task(repo.nextId(), "CS 314", "One", TaskType.QUIZ, DUE));
        repo.save(new Task(repo.nextId(), "CS 314", "Two", TaskType.QUIZ, DUE));

        assertEquals(3, new CsvTaskRepository(file()).nextId());
    }

    @Test
    void deletesAreSaved() {
        CsvTaskRepository repo = new CsvTaskRepository(file());
        repo.save(new Task(1, "CS 314", "Gone soon", TaskType.EXAM, DUE));
        assertTrue(repo.delete(1));
        assertFalse(repo.delete(1));

        assertTrue(new CsvTaskRepository(file()).findAll().isEmpty());
    }

    @Test
    void noFileYetMeansNoTasks() {
        assertTrue(new CsvTaskRepository(file()).findAll().isEmpty());
        assertFalse(Files.exists(file())); // just reading shouldn't create anything
    }

    @Test
    void aCorruptLineFailsLoudlyInsteadOfSilentlyLosingData() throws IOException {
        Files.writeString(file(), CsvTaskRepository.HEADER + "\nthis,is,not,a,task\n");

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> new CsvTaskRepository(file()));
        assertTrue(e.getMessage().contains("Line 2"), e.getMessage());
    }

    @Test
    void csvParsingHandlesQuotesAndEmptyFields() {
        assertEquals(List.of("a", "b,c", "say \"hi\"", ""), Csv.parseLine("a,\"b,c\",\"say \"\"hi\"\"\","));
        assertEquals("\"b,c\"", Csv.escape("b,c"));
        assertEquals("plain", Csv.escape("plain"));
    }
}
