package com.studytracker;

import com.studytracker.cli.CommandLineInterface;
import com.studytracker.service.TaskService;
import com.studytracker.storage.CsvTaskRepository;
import com.studytracker.storage.TaskRepository;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Scanner;

/**
 * Entry point. Its only job is wiring the pieces together: storage -> service -> CLI.
 *
 * <p>Run with no arguments for the interactive mode, or pass a command to run it once:
 * {@code java -jar studytracker.jar upcoming 3}
 */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        Path dataFile = dataFile();
        TaskRepository repository;
        try {
            repository = new CsvTaskRepository(dataFile);
        } catch (IllegalStateException | UncheckedIOException e) {
            System.err.println("Couldn't load your tasks: " + e.getMessage());
            System.exit(1);
            return;
        }

        TaskService service = new TaskService(repository, Clock.systemDefaultZone());
        try (Scanner scanner = new Scanner(System.in)) {
            CommandLineInterface cli = new CommandLineInterface(service, scanner, System.out);
            if (args.length > 0) {
                cli.execute(String.join(" ", args));
            } else {
                cli.runInteractive();
            }
        }
    }

    /** ~/.studytracker/tasks.csv by default. Override with -Dstudytracker.data=some/other/file.csv */
    private static Path dataFile() {
        String override = System.getProperty("studytracker.data");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home"), ".studytracker", "tasks.csv");
    }
}
