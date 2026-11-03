package de.glasslauncher.tv.ui

internal enum class UiSound { LEFT, RIGHT, UP, DOWN, CONFIRM }

/** Eligibility window only: input/focus never waits for a sound or a timer. */
internal class UiSoundFeedback(private val now: () -> Long, private val play: (UiSound) -> Unit) {
    var enabled=true
    private var focus: Any?=null
    private data class Move(val sound: UiSound,val origin: Any?,val at: Long)
    private var pending: Move?=null
    fun direction(sound: UiSound) {
        pending=Move(sound,focus,now())
    }
    fun cancelNavigation() { pending=null }
    fun focused(token: Any) {
        if (token==focus) return
        focus=token
        val move=pending ?: return
        pending=null
        val time=now()
        if (move.origin!=null && token!=move.origin && time-move.at in 0..250 && enabled) {
            play(move.sound)
        }
    }
    fun confirm() {
        pending=null
        if(enabled) play(UiSound.CONFIRM)
    }
}
