package bg.qa.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DateInputTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(delimiter = '|', value = {
            "2026-11-08         | 2026-11-08",
            "2026/11/8          | 2026-11-08",
            "2026.11.08         | 2026-11-08",
            "08.11.2026         | 2026-11-08",
            "8.11.2026          | 2026-11-08",
            "8/11/2026          | 2026-11-08",
            "08-11-26           | 2026-11-08",
            "8 11 2026          | 2026-11-08",
            "11/25/2026         | 2026-11-25",
            "25/11/2026         | 2026-11-25",
            "20261108           | 2026-11-08",
            "08112026           | 2026-11-08",
            "081126             | 2026-11-08",
            "8 November 2026    | 2026-11-08",
            "November 8, 2026   | 2026-11-08",
            "Nov 8th 2026       | 2026-11-08",
            "8th of nov, 2026   | 2026-11-08",
            "Sunday 8 Nov 2026  | 2026-11-08",
            "2026 nov 8         | 2026-11-08",
            "8nov2026           | 2026-11-08",
            "8-Nov-26           | 2026-11-08",
            "  NOV   8   2026   | 2026-11-08",
            "8 ноември 2026 г.  | 2026-11-08",
            "8 ное 2026         | 2026-11-08",
            "1 май 2026         | 2026-05-01",
            "1 мар 2026         | 2026-03-01",
            "Dec 24             | 2026-12-24",
            "24.12              | 2026-12-24",
            "15                 | 2026-11-15",
            "today              | 2026-11-08",
            "днес               | 2026-11-08",
            "tomorrow           | 2026-11-09",
            "yesterday          | 2026-11-07",
            "+3 days            | 2026-11-11",
            "in 2 weeks         | 2026-11-22",
            "1 month ago        | 2026-10-08",
            "-1 year            | 2025-11-08",
            "3 месеца           | 2027-02-08",
            "next month         | 2026-12-01",
            "November 2026      | 2026-11-01",
            "11/2026            | 2026-11-01",
            "2026-11            | 2026-11-01",
            "202611             | 2026-11-01",
            "2026               | 2026-01-01",
    })
    void startDates(String input, LocalDate expected) {
        assertEquals(expected, DateInput.parse(input, TODAY, false).date());
    }

    @ParameterizedTest(name = "end \"{0}\" -> {1}")
    @CsvSource(delimiter = '|', value = {
            "November 2026  | 2026-11-30",
            "feb 2028       | 2028-02-29",
            "2026           | 2026-12-31",
            "next month     | 2026-12-31",
            "next week      | 2026-11-15",
            "31.12.2026     | 2026-12-31",
    })
    void endDates(String input, LocalDate expected) {
        assertEquals(expected, DateInput.parse(input, TODAY, true).date());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "banana", "31.02.2026", "13/13/2026", "1 2 3 4", "ju 8 2026", "8 nov dec 2026"})
    void rejectsNonsense(String input) {
        assertThrows(RuntimeException.class, () -> DateInput.parse(input, TODAY, false));
    }

    @Test
    void ambiguousDatesAreDayFirstWithANote() {
        DateInput.Result result = DateInput.parse("01/02/2026", TODAY, false);

        assertEquals(LocalDate.of(2026, 2, 1), result.date());
        assertEquals("read as day/month; type 2026-01-02 if you meant month/day", result.note());
    }
}
