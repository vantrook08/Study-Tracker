package com.studytracker.model;

/** Where a task is in its lifecycle. */
public enum Status {
    TODO("todo"),
    IN_PROGRESS("in progress"),
    DONE("done");

    private final String label;

    Status(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
