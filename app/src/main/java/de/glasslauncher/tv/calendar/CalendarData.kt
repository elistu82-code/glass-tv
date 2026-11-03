package de.glasslauncher.tv.calendar

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

internal enum class DayMove { LEFT, RIGHT, UP, DOWN }
internal fun moveDay(date: LocalDate, direction: DayMove): LocalDate = date.plusDays(when (direction) {
    DayMove.LEFT -> -1L; DayMove.RIGHT -> 1L; DayMove.UP -> -7L; DayMove.DOWN -> 7L
})

/** Monday-first, fixed six-week layout; blank cells never accept focus. */
internal fun monthDays(month: YearMonth): List<LocalDate?> {
    val offset = month.atDay(1).dayOfWeek.value - 1
    return List(42) { index -> (index - offset + 1).takeIf { it in 1..month.lengthOfMonth() }?.let(month::atDay) }
}

internal data class CalendarEntry(val id: String, val date: LocalDate, val time: LocalTime, val title: String, val details: String)

/** Deliberately local demonstration data. No account, persistence or calendar permission. */
internal fun demoEntries(today: LocalDate) = listOf(
    CalendarEntry("planning", today, LocalTime.of(9, 0), "Demo: Tagesplanung", "Beispiel für einen lokalen Kalendereintrag. Keine Synchronisation oder Erinnerung eingerichtet."),
    CalendarEntry("movie", today, LocalTime.of(20, 15), "Demo: Filmabend", "Ein zweiter auswählbarer Demo-Termin am selben Tag."),
    CalendarEntry("walk", today.plusDays(2), LocalTime.of(18, 0), "Demo: Spaziergang", "Dieser Beispieltermin liegt zwei Tage nach dem heutigen Datum."),
    CalendarEntry("week", today.plusDays(7), LocalTime.of(19, 0), "Demo: Wochenvorschau", "Mit Oben/Unten lässt sich eine ganze Woche weiter navigieren."),
)
