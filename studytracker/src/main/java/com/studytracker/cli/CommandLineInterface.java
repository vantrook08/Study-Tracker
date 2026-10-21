package com.studytracker.cli;

import com.studytracker.model.CourseSummary;
import com.studytracker.model.Status;
import com.studytracker.model.Task;
import com.studytracker.model.TaskType;
import com.studytracker.service.TaskService;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Scanner;
import java.util.function.Function;

/**
 * Reads commands, calls the service, prints results. This class should contain no "rules"
 * of its own; if you find yourself writing logic about due dates or grades here, it probably
 * belongs in TaskService instead.
 */
public class CommandLineInterface {
    private static final String HELP = """
            Commands:
              add                    add an assignment, quiz, exam, or project (asks you questions)
              list [all] [course]    open items, soonest first ('all' includes finished ones)
              upcoming [days]        items due in the next N days (default 7)
              overdue                items past their due date
              start <id...>          mark as in progress
              done <id...>           mark as done (e.g. 'done 3 4 5')
              reopen <id...>         mark as not done
              score <id> <score>     record a grade, e.g. 'score 4 92' or 'score 4 46/50'
              delete <id>            remove an item
              stats                  per-course summary
              help                   show this list
              quit                   exit
            """;

    private final TaskService service;
    private final Scanner in;
    private final PrintStream out;

    public CommandLineInterface(TaskService service, Scanner in, PrintStream out) {
        this.service = Objects.requireNonNull(service, "service");
        this.in = Objects.requireNonNull(in, "in");
        this.out = Objects.requireNonNull(out, "out");
    }

    /** The interactive loop: show the dashboard, then read commands until 'quit'. */
    public void runInteractive() {
        out.println("StudyTracker - type 'help' for commands, 'quit' to exit.");
        out.println();
        printDashboard();
        while (true) {
            out.print("> ");
            out.flush();
            if (!in.hasNextLine()) {
                break;
            }
            String line = in.nextLine().trim();
            if (line.isEmpty()) {
                continue;
            }
            if (!execute(line)) {
                break;
            }
            out.println();
        }
        out.println("Bye!");
    }

    /**
     * Runs one command. Returns false if the user asked to quit.
     * Errors are printed, never thrown, so one typo doesn't crash the whole program.
     */
    public boolean execute(String line) {
        String[] words = line.trim().split("\\s+");
        String command = words[0].toLowerCase(Locale.ROOT);
        List<String> args = List.of(words).subList(1, words.length);

        try {
            switch (command) {
                case "add" -> add();
                case "list", "ls" -> list(args);
                case "upcoming", "up" -> upcoming(args);
                case "overdue" -> printTasks(service.overdue(), "Nothing overdue. Nice.");
                case "start" -> changeStatus(args, Status.IN_PROGRESS);
                case "done" -> changeStatus(args, Status.DONE);
                case "reopen" -> changeStatus(args, Status.TODO);
                case "score" -> score(args);
                case "delete", "rm" -> delete(args);
                case "stats" -> stats();
                case "help", "?" -> out.print(HELP);
                case "quit", "exit", "q" -> {
                    return false;
                }
                default -> out.println("Unknown command '" + command + "'. Type 'help' to see what's available.");
            }
        } catch (IllegalArgumentException e) {
            out.println("Error: " + e.getMessage());
        } catch (UncheckedIOException e) {
            out.println("Error: couldn't save your changes. " + e.getMessage());
        }
        return true;
    }

    // ----- commands -----

    private void add() {
        out.println("New item (type 'cancel' at any prompt to stop):");
        try {
            String course = ask("Course (e.g. CS 314)", CommandLineInterface::requireText);
            String title = ask("Title", CommandLineInterface::requireText);
            TaskType type = ask("Type [assignment/quiz/exam/project]", TaskType::parse);
            LocalDateTime due = ask("Due (e.g. fri, tomorrow 2pm, 10/27 11:59pm)",
                    text -> DueDateParser.parse(text, service.now().toLocalDate()));

            Task task = service.add(course, title, type, due);
            out.println("Added #" + task.getId() + ": " + TaskFormatter.describe(task, service.now()));
            if (task.getDue().isBefore(service.now())) {
                out.println("Heads up: that due date has already passed.");
            }
        } catch (Cancelled e) {
            out.println("Cancelled, nothing was added.");
        }
    }

    private void list(List<String> args) {
        boolean includeDone = !args.isEmpty() && args.get(0).equalsIgnoreCase("all");
        List<String> courseWords = includeDone ? args.subList(1, args.size()) : args;
        String course = courseWords.isEmpty() ? null : String.join(" ", courseWords);

        String emptyMessage = course == null
                ? "Nothing on your list. Add something with 'add'."
                : "Nothing found for " + course.toUpperCase(Locale.ROOT) + ".";
        printTasks(service.list(course, includeDone), emptyMessage);
    }

    private void upcoming(List<String> args) {
        int days = args.isEmpty() ? 7 : parseWholeNumber(args.get(0), "Number of days");
        List<Task> tasks = service.upcoming(days);
        if (!tasks.isEmpty()) {
            out.println("Due in the next " + days + (days == 1 ? " day:" : " days:"));
        }
        printTasks(tasks, "Nothing due in the next " + days + (days == 1 ? " day." : " days."));
    }

    private void changeStatus(List<String> args, Status status) {
        if (args.isEmpty()) {
            throw new IllegalArgumentException("Which item? Give its id, e.g. 'done 3'.");
        }
        List<Integer> ids = new ArrayList<>();
        for (String arg : args) {
            ids.add(parseId(arg));
        }
        // Check every id exists before changing anything, so a typo doesn't leave things half-done.
        ids.forEach(service::get);
        for (int id : ids) {
            Task task = service.setStatus(id, status);
            out.println("#" + id + " " + task.getTitle() + " -> " + status.label());
        }
    }

    private void score(List<String> args) {
        if (args.size() != 2) {
            throw new IllegalArgumentException("Usage: score <id> <score>, e.g. 'score 4 92' or 'score 4 46/50'.");
        }
        int id = parseId(args.get(0));
        double percent = parseScore(args.get(1));
        Task task = service.recordScore(id, percent);
        out.println("Recorded " + TaskFormatter.percent(percent) + "% for #" + id + " "
                + task.getTitle() + " (marked done).");
    }

    private void delete(List<String> args) {
        if (args.size() != 1) {
            throw new IllegalArgumentException("Usage: delete <id>");
        }
        int id = parseId(args.get(0));
        Task task = service.get(id);
        out.print("Delete #" + id + " " + task.getTitle() + "? [y/N] ");
        out.flush();
        String answer = in.hasNextLine() ? in.nextLine().trim().toLowerCase(Locale.ROOT) : "";
        if (answer.equals("y") || answer.equals("yes")) {
            service.delete(id);
            out.println("Deleted.");
        } else {
            out.println("Kept it.");
        }
    }

    private void stats() {
        List<CourseSummary> summaries = service.summarizeByCourse();
        if (summaries.isEmpty()) {
            out.println("No data yet. Add something with 'add'.");
            return;
        }
        out.print(TaskFormatter.courseTable(summaries));
    }

    private void printDashboard() {
        List<Task> overdue = service.overdue();
        List<Task> thisWeek = service.upcoming(7);
        if (!overdue.isEmpty()) {
            out.println("OVERDUE (" + overdue.size() + "):");
            out.print(TaskFormatter.table(overdue, service.now()));
            out.println();
        }
        if (!thisWeek.isEmpty()) {
            out.println("Due in the next 7 days (" + thisWeek.size() + "):");
            out.print(TaskFormatter.table(thisWeek, service.now()));
            out.println();
        }
        if (overdue.isEmpty() && thisWeek.isEmpty()) {
            out.println("Nothing overdue and nothing due this week.");
            out.println();
        }
    }

    // ----- helpers -----

    private void printTasks(List<Task> tasks, String emptyMessage) {
        if (tasks.isEmpty()) {
            out.println(emptyMessage);
        } else {
            out.print(TaskFormatter.table(tasks, service.now()));
        }
    }

    /**
     * Asks a question and keeps asking until the answer parses. The generic type T means the
     * same method works whether the answer should become a String, a TaskType, or a date.
     */
    private <T> T ask(String question, Function<String, T> parser) {
        while (true) {
            out.print("  " + question + ": ");
            out.flush();
            if (!in.hasNextLine()) {
                throw new Cancelled(); // input ran out
            }
            String answer = in.nextLine().trim();
            if (answer.equalsIgnoreCase("cancel")) {
                throw new Cancelled();
            }
            try {
                return parser.apply(answer);
            } catch (IllegalArgumentException e) {
                out.println("  " + e.getMessage());
            }
        }
    }

    private static String requireText(String text) {
        if (text.isBlank()) {
            throw new IllegalArgumentException("This one's required.");
        }
        return text;
    }

    static int parseId(String text) {
        String digits = text.startsWith("#") ? text.substring(1) : text; // allow "#3"
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + text + "' isn't an id. Use the number from the ID column.");
        }
    }

    static int parseWholeNumber(String text, String what) {
        try {
            int value = Integer.parseInt(text);
            if (value < 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(what + " should be a whole number like 3, not '" + text + "'.");
        }
    }

    /** "92", "92%", and "46/50" all work. Returns a percent. */
    static double parseScore(String text) {
        String s = text.trim().replace("%", "");
        try {
            if (s.contains("/")) {
                String[] parts = s.split("/");
                if (parts.length != 2) {
                    throw new NumberFormatException();
                }
                double earned = Double.parseDouble(parts[0]);
                double possible = Double.parseDouble(parts[1]);
                if (possible <= 0) {
                    throw new IllegalArgumentException("Points possible has to be more than 0.");
                }
                return earned / possible * 100.0;
            }
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + text + "' isn't a score. Use a percent like 92 or points like 46/50.");
        }
    }

    /** Thrown when the user types 'cancel' in the middle of 'add'. */
    private static final class Cancelled extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
