package de.glasslauncher.tv.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import de.glasslauncher.tv.testing.AwakeDeviceRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalTestApi::class)
class CalendarScreenTest {
    @get:Rule(order = 0) val awake = AwakeDeviceRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun keepTestWindowAwake() {
        compose.activityRule.scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    private fun show() {
        compose.setContent { MaterialTheme { Box(Modifier.fillMaxSize()) {
            CalendarOverlay(remember { FocusRequester() }, onClose = {}, today = LocalDate.of(2024, 2, 28))
        } } }
    }
    @Test fun arrowsCrossLeapDayAndMonthAndMoveByWholeWeeks() {
        show()
        compose.onNodeWithTag("calendar-day:2024-02-28").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("calendar-day:2024-02-29").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("calendar-day:2024-03-01").assertIsFocused()
        compose.onNodeWithText("März 2024").assertIsDisplayed()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("calendar-day:2024-02-23").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("calendar-day:2024-03-01").assertIsFocused()
    }
    @Test fun demoEntriesAreSelectableAndEmptyDaysStayUsable() {
        show()
        compose.onNodeWithText("Demo-Kalender · nur lokal").assertIsDisplayed()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("calendar-event:planning").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("calendar-event-detail").assertIsDisplayed()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("calendar-event:movie").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft); pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("calendar-day:2024-02-29").assertIsFocused()
        compose.onNodeWithText("Keine Einträge").assertIsDisplayed()
        compose.onNodeWithTag("calendar-event:planning").assertDoesNotExist()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("calendar-close").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("calendar-day:2024-02-29").assertIsFocused()
    }
    @Test fun rapidRepeatedKeysKeepEveryDateStep() {
        show()
        compose.onRoot().performKeyInput { repeat(3) { pressKey(Key.DirectionRight) } }
        compose.onNodeWithTag("calendar-day:2024-03-02").assertIsFocused()
    }
}
