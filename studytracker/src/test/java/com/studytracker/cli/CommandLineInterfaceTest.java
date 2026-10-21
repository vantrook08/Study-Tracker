package com.studytracker.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.studytracker.model.Status;
import com.studytracker.model.TaskType;
import com.studytracker.service.TaskService;
import com.studytracker.storage.InMemoryTaskRepository;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Scanner;
import org.junit.jupiter.api.Test;

/** Drives the CLI with fake keyboard input and checks what it prints. */
class CommandLineInterfaceTest {
    private static final ZoneId ZONE = ZoneId.of("America/Chicago");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 10, 0);

    private final ByteArrayOutputStream printed = new ByteArrayOutputStream();
    private TaskService service;

    private CommandLineInterface cliWithInput(String typed) {
        service = new TaskService(new InMemoryTaskRepository(), Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));
        return new CommandLineInterface(service, new Scanner(typed),
                new PrintStream(printed, true, StandardCharsets.UTF_8));
    }

    private String output() {
        return printed.toString(StandardCharsets.UTF_8);
    }

    @Test
    void addAsksQuestionsThenShowsTheItemInTheList() {
        CommandLineInterface cli = cliWithInput("CS 314\nProject 3\np\nfri\n");

        cli.execute("add");
        cli.execute("list");

        assertTrue(output().contains("Added #1"), output());
        assertTrue(output().contains("Project 3"), output());
        assertTrue(output().contains("in 3 days"), output());
    }

    @Test
    void badAnswersAreRetriedInsteadOfCrashing() {
        CommandLineInterface cli = cliWithInput("CS 314\nQuiz 1\nnot-a-type\nquiz\nsomeday\ntomorrow\n");

        cli.execute("add");

        assertTrue(output().contains("Unknown type"), output());
        assertTrue(output().contains("Couldn't understand the date"), output());
        assertEquals(1, service.list(null, false).size());
    }

    @Test
    void cancelStopsAddWithoutSavingAnything() {
        CommandLineInterface cli = cliWithInput("CS 314\ncancel\n");

        cli.execute("add");

        assertTrue(output().contains("Cancelled"), output());
        assertTrue(service.list(null, true).isEmpty());
    }

    @Test
    void doneWithABadIdChangesNothing() {
        CommandLineInterface cli = cliWithInput("");
        service.add("CS 314", "A1", TaskType.ASSIGNMENT, NOW.plusDays(1));

        cli.execute("done 1 99");

        assertTrue(output().contains("no task with id 99"), output());
        assertEquals(Status.TODO, service.get(1).getStatus()); // #1 was not half-applied
    }

    @Test
    void quitReturnsFalseAndTyposDoNot() {
        CommandLineInterface cli = cliWithInput("");
        assertTrue(cli.execute("lsit"));
        assertFalse(cli.execute("quit"));
    }

    @Test
    void scoresCanBePercentsOrPoints() {
        assertEquals(92.0, CommandLineInterface.parseScore("92"), 1e-9);
        assertEquals(92.0, CommandLineInterface.parseScore("92%"), 1e-9);
        assertEquals(92.0, CommandLineInterface.parseScore("46/50"), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> CommandLineInterface.parseScore("A+"));
        assertThrows(IllegalArgumentException.class, () -> CommandLineInterface.parseScore("5/0"));
    }
}
