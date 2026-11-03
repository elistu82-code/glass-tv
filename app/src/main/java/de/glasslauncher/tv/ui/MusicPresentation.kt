package de.glasslauncher.tv.ui

import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import de.glasslauncher.tv.media.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect

internal object MusicLayout {
    val buttonWidth=64.dp
    val buttonHeight=56.dp
    val buttonGap=24.dp
    val titleHeight=32.dp
    val artistHeight=24.dp
    val coverSize=170.dp
    val coverTop=100.dp
    val controlsLift=44.dp
    val controlsLeftShift=24.dp
    val progressHeight=18.dp
    val progressLift=(-11).dp
    const val infoWidthFraction=.5f
}

internal fun usableMusicArt(art:android.graphics.Bitmap?)=art?.takeIf {
    !it.isRecycled && suitableMusicArtwork(it.width,it.height,false)
}

@Composable
internal fun MusicAlbumCover(music:SpotifyState,modifier:Modifier=Modifier) {
    val config=LocalGlassConfig.current
    Box(modifier.clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha=.06f))
        .border(1.dp,Color.White.copy(alpha=.14f),RoundedCornerShape(22.dp)).testTag("music-album-cover"),contentAlignment=Alignment.Center) {
        Crossfade(usableMusicArt(music.artwork),animationSpec=tween(AnimationTokens.duration(AnimationTokens.music,config),easing=AnimationTokens.easing(config)),label="Music portrait") { art ->
            if(art!=null) Image(art.asImageBitmap(),"Aktuelles Albumcover",Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
            else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Text("♪",fontSize=48.sp,color=Color.White.copy(alpha=.35f)) }
        }
    }
}

@Composable
internal fun MusicProgress(music:SpotifyState,modifier:Modifier=Modifier) {
    val clock=remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val owner=LocalLifecycleOwner.current
    val reduced=LocalGlassConfig.current.reducedMotion
    LaunchedEffect(owner,music.isPlaying,music.durationMs,reduced) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if(music.isPlaying && music.durationMs>0) while(isActive) {
                clock.longValue=SystemClock.elapsedRealtime();delay(if(reduced) 1000 else 33)
            }
        }
    }
    fun progress()=songProgress(music.positionMs,music.durationMs,music.positionUpdatedAtMs,music.playbackSpeed,music.isPlaying,clock.longValue)
    val announcedProgress=songProgress(music.positionMs,music.durationMs,0,0f,false,0)
    Spacer(modifier.height(MusicLayout.progressHeight).testTag("music-progress").semantics {
        contentDescription="Liedfortschritt"
        announcedProgress?.let { progressBarRangeInfo=ProgressBarRangeInfo(it,0f..1f) } ?: run { stateDescription="Fortschritt nicht verfügbar" }
    }.graphicsLayer().drawWithCache {
        // Size-dependent geometry, paints and one mutable Path are cached, never rebuilt per frame.
        // Read the clock only in onDrawBehind: this small ribbon invalidates, not the dashboard.
        val steps=96
        val xs=FloatArray(steps+1) { size.width*it/steps }
        val envelope=FloatArray(steps+1) { sin(it.toFloat()/steps*PI).toFloat()*4.dp.toPx() }
        val primarySin=FloatArray(steps+1) { sin(it.toFloat()/steps*8*PI).toFloat() }
        val primaryCos=FloatArray(steps+1) { cos(it.toFloat()/steps*8*PI).toFloat() }
        val secondarySin=FloatArray(steps+1) { sin(it.toFloat()/steps*14*PI).toFloat() }
        val secondaryCos=FloatArray(steps+1) { cos(it.toFloat()/steps*14*PI).toFloat() }
        val ribbon=Path()
        val stroke=Stroke(1.6.dp.toPx(),cap=StrokeCap.Round)
        val glow=Stroke(5.dp.toPx(),cap=StrokeCap.Round)
        val light=Brush.horizontalGradient(listOf(Color.White.copy(alpha=.58f),Color.White.copy(alpha=.95f)))
        onDrawBehind {
            val phase=if(reduced) 0f else (clock.longValue%24000L)/12000f*(2*PI).toFloat()
            val s=sin(phase);val c=cos(phase)
            val s2=sin(phase*.5f);val c2=cos(phase*.5f)
            fun pointY(index:Int)=size.height/2+envelope[index]*(
                .82f*(primarySin[index]*c-primaryCos[index]*s)+
                .18f*(secondarySin[index]*c2+secondaryCos[index]*s2))
            ribbon.reset();ribbon.moveTo(0f,pointY(0))
            for(index in 1..steps) ribbon.lineTo(xs[index],pointY(index))
            drawPath(ribbon,Color.White.copy(alpha=.19f),style=stroke)
            progress()?.let { fraction ->
                val end=size.width*fraction
                if(end>0) {
                    clipRect(right=end) {
                        drawPath(ribbon,Color.White.copy(alpha=.09f),style=glow)
                        drawPath(ribbon,light,style=stroke)
                    }
                    val position=fraction*steps
                    val index=position.toInt().coerceIn(0,steps)
                    val next=(index+1).coerceAtMost(steps)
                    val y=pointY(index)+(pointY(next)-pointY(index))*(position-index)
                    val head=Offset(end,y)
                    drawCircle(Color.White.copy(alpha=.12f),3.dp.toPx(),head)
                    drawCircle(Color.White.copy(alpha=.95f),1.7.dp.toPx(),head)
                }
            }
        }
    })

}

@Composable
internal fun CompactMusicVisualizer(animate:Boolean) {
    val phase=if(!animate) 0f else {
        val transition=rememberInfiniteTransition(label="Music simulation")
        val value by transition.animateFloat(0f,6.283f,infiniteRepeatable(tween(4500,easing=LinearEasing)),label="Music bars")
        value
    }
    Column(Modifier.width(104.dp).testTag("spotify-visualizer").semantics {
        stateDescription=if(animate) "Simulation läuft" else "Simulation pausiert"
    },horizontalAlignment=Alignment.End) {
        Canvas(Modifier.width(88.dp).height(12.dp)) {
            val step=size.width/16
            repeat(16) { index ->
                val h=(.25f+.65f*((sin(phase+index*.71f)+1)/2))*size.height
                drawRoundRect(Color.White.copy(alpha=.45f),Offset(index*step,size.height-h),Size(step*.4f,h),CornerRadius(1f,1f))
            }
        }
        Text("Simulation",fontSize=8.sp,lineHeight=10.sp,color=Color.White.copy(alpha=.4f))
    }
}
