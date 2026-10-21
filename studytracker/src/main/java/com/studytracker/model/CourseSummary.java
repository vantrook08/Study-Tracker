package com.studytracker.model;

import java.util.OptionalDouble;

/**
 * Totals for one course. A record is a compact, immutable data class: Java generates the
 * constructor, accessors (course(), open(), ...), equals, hashCode, and toString for you.
 */
public record CourseSummary(String course, int open, int done, int overdue, OptionalDouble averageScore) {

    public int total() {
        return open + done;
    }
}
