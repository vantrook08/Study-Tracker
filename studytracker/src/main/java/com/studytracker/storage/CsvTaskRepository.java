package com.studytracker.storage;

import com.studytracker.model.Status;
import com.studytracker.model.Task;
import com.studytracker.model.TaskType;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Saves tasks to a CSV file (you can open it in Excel). Everything is loaded into memory at
 * startup, and the whole file is rewritten after every change. For a few hundred tasks that's
 * instant, and it keeps the code simple.
 */
public final class CsvTaskRepository extends InMemoryTaskRepository {
    static final String HEADER = "id,course,title,type,due,status,score";
    private static final int FIELD_COUNT = 7;

    private final Path file;

    public CsvTaskRepository(Path file) {
        this.file = Objects.requireNonNull(file, "file");
        load();
    }

    @Override
    public void save(Task task) {
        super.save(task);
        writeAll();
    }

    @Override
    public boolean delete(int id) {
        boolean removed = super.delete(id);
        if (removed) {
            writeAll();
        }
        return removed;
    }

    private void load() {
        if (Files.notExists(file)) {
            return; // first run: nothing saved yet
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (i == 0) {
                line = line.replace("\uFEFF", ""); // Excel and Notepad sometimes add an invisible BOM
            }
            if (line.isBlank() || (i == 0 && line.equals(HEADER))) {
                continue;
            }
            try {
                store(fromCsv(line));
            } catch (RuntimeException e) {
                // Fail loudly instead of skipping the line. If we skipped it, the next save would
                // rewrite the file without it, and that task would be gone for good.
                throw new IllegalStateException("Line " + (i + 1) + " of " + file + " is invalid ("
                        + e.getMessage() + "). Fix or remove that line and try again.", e);
            }
        }
    }

    private void writeAll() {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        for (Task task : findAll()) {
            lines.add(toCsv(task));
        }

        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            // Write to a temp file first, then swap it into place. If the program crashes
            // mid-write, you lose the temp file instead of your real data.
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(temp, lines, StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save to " + file, e);
        }
    }

    static String toCsv(Task task) {
        String score = task.getScore().isPresent() ? String.valueOf(task.getScore().getAsDouble()) : "";
        return String.join(",",
                String.valueOf(task.getId()),
                Csv.escape(task.getCourse()),
                Csv.escape(task.getTitle()),
                task.getType().name(),
                task.getDue().toString(),        // ISO format, e.g. 2026-10-27T23:59
                task.getStatus().name(),
                score);
    }

    static Task fromCsv(String line) {
        List<String> fields = Csv.parseLine(line);
        if (fields.size() != FIELD_COUNT) {
            throw new IllegalArgumentException("expected " + FIELD_COUNT + " fields but found " + fields.size());
        }
        String score = fields.get(6);
        return new Task(
                Integer.parseInt(fields.get(0)),
                fields.get(1),
                fields.get(2),
                TaskType.valueOf(fields.get(3)),
                LocalDateTime.parse(fields.get(4)),
                Status.valueOf(fields.get(5)),
                score.isEmpty() ? null : Double.valueOf(score));
    }
}
