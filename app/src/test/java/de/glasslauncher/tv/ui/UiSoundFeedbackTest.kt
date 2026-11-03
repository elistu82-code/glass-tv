package de.glasslauncher.tv.ui

import org.junit.Assert.*
import org.junit.Test

class UiSoundFeedbackTest {
    private var time=1000L
    private val played=mutableListOf<UiSound>()
    private val feedback=UiSoundFeedback({time},played::add)
    @Test fun programmaticFocusAndBlockedDirectionAreSilent() {
        feedback.focused("first"); feedback.focused("other")
        feedback.direction(UiSound.LEFT); feedback.focused("other")
        assertTrue(played.isEmpty())
    }
    @Test fun realDirectionalFocusChangesPlayExactlyOnce() {
        feedback.focused("a")
        for ((index,sound) in listOf(UiSound.LEFT,UiSound.RIGHT,UiSound.UP,UiSound.DOWN).withIndex()) {
            time+=200; feedback.direction(sound); feedback.focused(index); feedback.focused(index)
        }
        assertEquals(listOf(UiSound.LEFT,UiSound.RIGHT,UiSound.UP,UiSound.DOWN),played)
    }
    @Test fun confirmationClearsPendingNavigationBeforeOverlayFocus() {
        feedback.focused("app"); feedback.direction(UiSound.UP); feedback.confirm(); feedback.focused("calendar")
        assertEquals(listOf(UiSound.CONFIRM),played)
    }
    @Test fun rapidNavigationSoundsEveryRealChangeExactlyOnce() {
        feedback.focused("a"); feedback.direction(UiSound.RIGHT); feedback.focused("b")
        time+=20; feedback.direction(UiSound.RIGHT); feedback.focused("c")
        time+=200; feedback.direction(UiSound.RIGHT); feedback.focused("d")
        assertEquals(listOf(UiSound.RIGHT,UiSound.RIGHT,UiSound.RIGHT),played)
    }
    @Test fun expiredInputDoesNotSoundOnLaterProgrammaticFocus() {
        feedback.focused("a"); feedback.direction(UiSound.RIGHT); time+=300; feedback.focused("b")
        assertTrue(played.isEmpty())
    }
    @Test fun disabledSoundsStillTrackFocusAndNeverEmit() {
        feedback.enabled=false; feedback.focused("a"); feedback.direction(UiSound.DOWN); feedback.focused("b"); feedback.confirm()
        assertTrue(played.isEmpty())
        feedback.enabled=true; feedback.direction(UiSound.UP); feedback.focused("c")
        assertEquals(listOf(UiSound.UP),played)
    }
}
