package de.glasslauncher.tv.calendar

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class CalendarDataTest {
    @Test fun arrowsCrossLeapDayMonthAndYearBoundaries() {
        val date = LocalDate.of(2024, 2, 28)
        assertEquals(LocalDate.of(2024, 2, 29), moveDay(date, DayMove.RIGHT))
        assertEquals(LocalDate.of(2024, 2, 27), moveDay(date, DayMove.LEFT))
        assertEquals(LocalDate.of(2024, 2, 21), moveDay(date, DayMove.UP))
        assertEquals(LocalDate.of(2024, 3, 6), moveDay(date, DayMove.DOWN))
        assertEquals(LocalDate.of(2025, 1, 1), moveDay(LocalDate.of(2024, 12, 31), DayMove.RIGHT))
    }
    @Test fun gridUsesMondayFirstSixWeeksAndIncludesLeapDay() {
        val days = monthDays(YearMonth.of(2024, 2))
        assertEquals(42, days.size)
        assertNull(days[0])
        assertEquals(LocalDate.of(2024, 2, 1), days[3])
        assertEquals(LocalDate.of(2024, 2, 29), days[31])
        assertNull(days[32])
        assertEquals(29, days.filterNotNull().distinct().size)
    }
    @Test fun sixWeekMonthKeepsItsLastDay() {
        val days = monthDays(YearMonth.of(2026, 3))
        assertEquals(LocalDate.of(2026, 3, 1), days[6])
        assertEquals(LocalDate.of(2026, 3, 31), days[36])
        assertEquals(31, days.filterNotNull().size)
    }
}
