package de.glasslauncher.tv.ui

import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import android.media.AudioAttributes
import android.media.AudioManager
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import de.glasslauncher.tv.testing.AwakeDeviceRule
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Explicit real-device output probe. Does not change streams, global settings or audio focus. */
class UiSoundPlayerTest {
    @get:Rule(order=0) val awake=AwakeDeviceRule()
    @get:Rule(order=1) val foreground=ActivityScenarioRule(ComponentActivity::class.java)
    @Test fun originalSamplesPreloadAndReachTheUnmutedMediaOutput() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveAudio")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val audio=context.getSystemService(AudioManager::class.java)
        val effects=Settings.System.getInt(context.contentResolver,Settings.System.SOUND_EFFECTS_ENABLED,0)
        assumeTrue("This probe targets the documented Fire OS mute case",effects==1 && audio.isStreamMute(AudioManager.STREAM_SYSTEM) && !audio.isStreamMute(AudioManager.STREAM_MUSIC) && audio.getStreamVolume(AudioManager.STREAM_MUSIC)>0)
        fun levels()=listOf(AudioManager.STREAM_SYSTEM,AudioManager.STREAM_MUSIC).map { audio.getStreamVolume(it) to audio.isStreamMute(it) }
        val before=levels()
        val player=UiSoundPlayer(context)
        val streams=mutableListOf<Int>()
        var activeMediaSonification=false
        try {
            assertTrue("Both real PCM assets must finish loading",player.prepare())
            assertEquals(UiSoundRoute.MEDIA,player.route())
            assertEquals(0,player.play(UiSound.LEFT,0f))
            for(sound in listOf(UiSound.LEFT,UiSound.RIGHT,UiSound.UP,UiSound.DOWN,UiSound.CONFIRM)) {
                streams+=player.play(sound,.5f)
                activeMediaSonification=activeMediaSonification || audio.activePlaybackConfigurations.any {
                    it.audioAttributes.usage==AudioAttributes.USAGE_MEDIA && it.audioAttributes.contentType==AudioAttributes.CONTENT_TYPE_SONIFICATION
                }
                delay(55)
            }
            assertTrue("Every request needs an actual SoundPool stream, no native silent effect: $streams, route=${player.route()}",streams.all { it>0 })
            assertEquals(before,levels())
            assertEquals(effects,Settings.System.getInt(context.contentResolver,Settings.System.SOUND_EFFECTS_ENABLED,0))
            File(context.cacheDir,"ui-sound-result.json").writeText("""{"route":"MEDIA","samplesLoaded":true,"acceptedStreams":${streams.size},"activeMediaSonification":$activeMediaSonification,"volumesUnchanged":true}""")
        } finally { player.close() }
        assertEquals("Released players cannot emit late feedback",0,player.play(UiSound.CONFIRM,.5f))
    }
}
