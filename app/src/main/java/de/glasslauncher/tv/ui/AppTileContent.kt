package de.glasslauncher.tv.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.glasslauncher.tv.core.InstalledApp
import de.glasslauncher.tv.data.AppIcons

@Composable
internal fun rememberAppIcon(app: InstalledApp): Bitmap? {
    val context=LocalContext.current.applicationContext
    val bitmap by produceState(AppIcons.cached(app),app.id,app.iconRevision,app.icon) { value=AppIcons.load(context,app) }
    return bitmap
}

@Composable
internal fun AppTileContent(app: InstalledApp, pinned: Boolean) {
    val icon=rememberAppIcon(app)
    Box(Modifier.fillMaxSize()) {
        if (icon != null) Image(icon.asImageBitmap(),null,Modifier.align(Alignment.TopCenter).fillMaxWidth().height(GlassTokens.appIconHeight).padding(horizontal=6.dp),contentScale=ContentScale.Fit)
        else Text(app.label.take(1),Modifier.align(Alignment.TopCenter),fontSize=40.sp)
        Text((if(pinned) "• " else "")+app.label,Modifier.align(Alignment.BottomCenter).padding(horizontal=2.dp),
            maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=12.sp,lineHeight=14.sp)
    }
}
