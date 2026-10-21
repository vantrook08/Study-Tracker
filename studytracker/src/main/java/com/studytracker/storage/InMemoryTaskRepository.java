package com.studytracker.storage;

import com.studytracker.model.Task;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Keeps tasks in a map and forgets them when the program exits. Handy for tests, and it's
 * the base class that {@link CsvTaskRepository} builds on.
 */
public class InMemoryTaskRepository implements TaskRepository {
    private final Map<Integer, Task> tasks = new LinkedHashMap<>();

    @Override
    public List<Task> findAll() {
        return List.copyOf(tasks.values()); // a copy, so callers can't add/remove behind our back
    }

    @Override
    public Optional<Task> findById(int id) {
        return Optional.ofNullable(tasks.get(id));
    }

    @Override
    public void save(Task task) {
        store(task);
    }

    @Override
    public boolean delete(int id) {
        return tasks.remove(id) != null;
    }

    @Override
    public int nextId() {
        return tasks.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
    }

    /** Puts a task in the map with no side effects. Subclasses use this while loading. */
    protected final void store(Task task) {
        Objects.requireNonNull(task, "task");
        tasks.put(task.getId(), task);
    }
}
