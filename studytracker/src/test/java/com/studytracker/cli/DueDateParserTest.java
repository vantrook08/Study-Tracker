package com.studytracker.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DueDateParserTest {
    // A Tuesday. Every test pretends "today" is this date so the results never change.
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

    // A parameterized test runs once per row: input text, then the expected result.
    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
            "'today',             2026-10-20T23:59",
            "'tomorrow 2pm',      2026-10-21T14:00",
            "'fri',               2026-10-23T23:59",
            "'Friday 9:30am',     2026-10-23T09:30",
            "'thu at 7pm',        2026-10-22T19:00",
            "'tue',               2026-10-27T23:59",   // next Tuesday, not today
            "'mon 12am',          2026-10-26T00:00",
            "'10/27',             2026-10-27T23:59",
            "'10/27 11:59 pm',    2026-10-27T23:59",
            "'9/1',               2026-09-01T23:59",   // recent past stays this year
            "'1/15',              2027-01-15T23:59",   // months ago, so it means next year
            "'12/1/27 noon',      2027-12-01T12:00",
            "'2026-11-03 14:00',  2026-11-03T14:00",
    })
    void understandsCommonFormats(String input, String expected) {
        assertEquals(LocalDateTime.parse(expected), DueDateParser.parse(input, TODAY));
    }

    @ParameterizedTest(name = "rejects \"{0}\"")
    @ValueSource(strings = {"", "   ", "someday", "2/30", "13/1", "fri 2", "fri 13pm", "fri 25:00", "fri 9:75"})
    void rejectsNonsense(String input) {
        assertThrows(IllegalArgumentException.class, () -> DueDateParser.parse(input, TODAY));
    }
}
