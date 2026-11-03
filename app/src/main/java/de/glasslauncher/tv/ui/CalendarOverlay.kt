package de.glasslauncher.tv.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.glasslauncher.tv.calendar.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun BoxScope.CalendarOverlay(
    firstFocus: FocusRequester,
    onClose: () -> Unit,
    today: LocalDate = LocalDate.now(),
    entries: List<CalendarEntry> = demoEntries(today),
) {
    var selectedDay by remember { mutableStateOf(today) }
    var openedEntry by remember(selectedDay) { mutableStateOf<String?>(null) }
    val month = YearMonth.from(selectedDay)
    val days = remember(month) { monthDays(month) }
    val dayEntries = entries.filter { it.date == selectedDay }.sortedBy { it.time }
    val closeFocus = remember { FocusRequester() }
    val eventFocus = remember { mutableMapOf<String, FocusRequester>() }
    val locale = Locale.GERMAN
    LaunchedEffect(selectedDay) { withFrameNanos { }; firstFocus.requestFocus() }
    Column(Modifier.align(Alignment.Center).fillMaxWidth(.86f).fillMaxHeight(.88f)
        .glassSurface(LocalGlassConfig.current, stronger = true).padding(24.dp).testTag("calendar-overlay"),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)), fontSize = 25.sp, fontWeight = FontWeight.Light)
                Text("Demo-Kalender · nur lokal", fontSize = 10.sp, color = Color.White.copy(alpha = .7f))
            }
            RemoteButton("Kalender schließen", Modifier.width(100.dp).focusRequester(closeFocus).focusProperties { left = firstFocus; down = firstFocus; right = closeFocus; up = closeFocus }.testTag("calendar-close"), onClick = onClose) {
                Text("Schließen", fontSize = 12.sp)
            }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEach {
                        Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, color = Color.White.copy(alpha = .7f))
                    }
                }
                days.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        week.forEach { date ->
                            if (date == null) Spacer(Modifier.weight(1f).height(38.dp))
                            else key(date) {
                                RemoteButton(date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", locale)),
                                    Modifier.weight(1f).height(38.dp).testTag("calendar-day:$date")
                                        .then(if (date == selectedDay) Modifier.focusRequester(firstFocus) else Modifier)
                                        .background(if (date == today) Color.White.copy(alpha = .055f) else Color.Transparent, RoundedCornerShape(16.dp))
                                        .onPreviewKeyEvent { event ->
                                            val direction = when (event.key) {
                                                Key.DirectionLeft -> DayMove.LEFT; Key.DirectionRight -> DayMove.RIGHT
                                                Key.DirectionUp -> DayMove.UP; Key.DirectionDown -> DayMove.DOWN; else -> null
                                            }
                                            if (event.type == KeyEventType.KeyDown && direction != null) {
                                                // Read current state, so rapid repeated keys do not reuse an old cell date.
                                                selectedDay = moveDay(selectedDay, direction); true
                                            } else false
                                        },
                                    contentPadding = PaddingValues(2.dp), onFocused = { selectedDay = date },
                                    onClick = { (dayEntries.firstOrNull()?.let { eventFocus[it.id] } ?: closeFocus).requestFocus() }) {
                                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${date.dayOfMonth}", fontSize = 15.sp, lineHeight = 17.sp,
                                            fontWeight = if (date == today) FontWeight.SemiBold else FontWeight.Normal)
                                        Text(if (entries.any { it.date == date }) "•" else " ", fontSize = 9.sp, lineHeight = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).testTag("calendar-agenda"),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(selectedDay.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", locale)), fontSize = 17.sp)
                if (dayEntries.isEmpty()) Text("Keine Einträge", fontSize = 13.sp, color = Color.White.copy(alpha = .7f))
                dayEntries.forEachIndexed { index, entry ->
                    val focus = eventFocus.getOrPut(entry.id) { FocusRequester() }
                    RemoteButton(entry.title, Modifier.fillMaxWidth().focusRequester(focus).testTag("calendar-event:${entry.id}")
                        .focusProperties { left = firstFocus; right = focus; up = if (index == 0) firstFocus else eventFocus[dayEntries[index - 1].id] ?: firstFocus
                            down = dayEntries.getOrNull(index + 1)?.let { eventFocus.getOrPut(it.id) { FocusRequester() } } ?: closeFocus },
                        onClick = { openedEntry = entry.id }) {
                        Column {
                            Text(entry.time.format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 11.sp, color = Color.White.copy(alpha = .75f))
                            Text(entry.title, fontSize = 13.sp)
                        }
                    }
                }
                dayEntries.find { it.id == openedEntry }?.let {
                    Text(it.details, Modifier.padding(top = 4.dp).testTag("calendar-event-detail"), fontSize = 12.sp, color = Color.White.copy(alpha = .9f))
                }
            }
        }
        Text("OK: Einträge öffnen · Zurück: Kalender schließen", fontSize = 10.sp, color = Color.White.copy(alpha = .65f))
    }
}
