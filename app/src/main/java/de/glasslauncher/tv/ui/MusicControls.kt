package de.glasslauncher.tv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.glasslauncher.tv.core.LauncherCallbacks
import de.glasslauncher.tv.media.SpotifyState

@Composable
internal fun MusicControls(music:SpotifyState,showInfo:Boolean,callbacks:LauncherCallbacks,
    previous:FocusRequester,play:FocusRequester,next:FocusRequester,
    weather:FocusRequester,clock:FocusRequester,dock:()->FocusRequester,
    modifier:Modifier=Modifier,onFocused:(Int)->Unit={},cover:FocusRequester?=null) {
    val focus=listOf(previous,play,next)
    val enabled=listOf(music.canPrevious,music.canPlayPause,music.canNext)
    val visuals=de.glasslauncher.tv.media.spotifyVisuals(LocalGlassConfig.current,music)
    Column(modifier.testTag("music-controls"),horizontalAlignment=Alignment.Start) {
        Box(Modifier.fillMaxWidth().heightIn(min=MusicLayout.titleHeight)) {
            if(showInfo) Text(music.title,Modifier.testTag("music-title"),fontSize=26.sp,lineHeight=32.sp,
                fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().heightIn(min=MusicLayout.artistHeight),verticalAlignment=Alignment.CenterVertically) {
            if(showInfo) Text(music.artist,Modifier.weight(1f).testTag("music-artist"),fontSize=16.sp,lineHeight=22.sp,
                color=Color.White.copy(alpha=.7f),maxLines=1,overflow=TextOverflow.Ellipsis)
            else Spacer(Modifier.weight(1f))
            if(visuals.visible) CompactMusicVisualizer(visuals.animate)
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(MusicLayout.buttonGap),modifier=Modifier.testTag("music-buttons")) {
            listOf("Vorheriger Titel",if(music.isPlaying) "Pause" else "Wiedergabe","Nächster Titel").forEachIndexed { index,label ->
                RemoteButton(label,Modifier.width(MusicLayout.buttonWidth).height(MusicLayout.buttonHeight).background(GlassTokens.tint.copy(alpha=LocalGlassConfig.current.glassOpacity.coerceIn(.1f,.6f)*.8f),RoundedCornerShape(16.dp))
                    .focusRequester(focus[index]).testTag("music:$index").focusProperties {
                        left=(index-1 downTo 0).firstOrNull { enabled[it] }?.let { focus[it] } ?: focus[index]
                        right=(index+1..2).firstOrNull { enabled[it] }?.let { focus[it] } ?: cover ?: focus[index]
                        down=dock();up=clock
                    },enabled=music.connected && enabled[index],contentPadding=PaddingValues(0.dp),
                    onFocused={onFocused(index)},onClick=when(index) {
                        0->callbacks.onMusicPrevious;1->callbacks.onMusicPlayPause;else->callbacks.onMusicNext
                    }) { Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { TransportIcon(index,music.isPlaying) } }
            }
        }
    }
}

@Composable
private fun TransportIcon(index:Int,playing:Boolean) {
    Canvas(Modifier.size(30.dp)) {
        val unit=size.width/30f
        val color=Color.White.copy(alpha=.95f)
        if(index==1 && playing) {
            for(x in listOf(8f,18f)) drawRoundRect(color,Offset(x*unit,5*unit),Size(4*unit,20*unit),CornerRadius(unit,unit))
        } else {
            val path=Path()
            if(index==0) {
                path.moveTo(22*unit,5*unit);path.lineTo(8*unit,15*unit);path.lineTo(22*unit,25*unit)
                drawRoundRect(color,Offset(5*unit,5*unit),Size(3*unit,20*unit),CornerRadius(unit,unit))
            } else {
                path.moveTo(9*unit,5*unit);path.lineTo(23*unit,15*unit);path.lineTo(9*unit,25*unit)
                if(index==2) drawRoundRect(color,Offset(23*unit,5*unit),Size(3*unit,20*unit),CornerRadius(unit,unit))
            }
            path.close();drawPath(path,color)
        }
    }
}
