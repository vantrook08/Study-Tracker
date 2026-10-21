package com.studytracker.storage;

import com.studytracker.model.Task;
import java.util.List;
import java.util.Optional;

/**
 * Where tasks live. The rest of the app only talks to this interface, never to a specific
 * implementation, so you could swap the CSV file for a database later without touching the
 * service or CLI code.
 */
public interface TaskRepository {

    List<Task> findAll();

    Optional<Task> findById(int id);

    /** Inserts the task, or replaces the existing one with the same id. */
    void save(Task task);

    /** Returns true if something was actually deleted. */
    boolean delete(int id);

    /** The id to give the next new task. */
    int nextId();
}
