package de.glasslauncher.tv.wallpaper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Shader
import de.glasslauncher.tv.core.LauncherConfig
import java.io.File

data class WallpaperFrames(val normal: Bitmap, val blurred: Bitmap)

/** Original procedurally drawn placeholder art. No remote assets or realtime blur. */
fun loadFrames(config: LauncherConfig): WallpaperFrames {
    return prepareFrames(loadWallpaperImage(config))
}

internal fun loadWallpaperImage(config: LauncherConfig): Bitmap {
    val path = config.wallpaperPath?.let { if (config.wallpaperVideo) "$it.jpg" else it }
    val image = path?.takeIf { File(it).isFile }?.let { BitmapFactory.decodeFile(it) } ?: drawScene(config.theme)
    return image
}

fun prepareFrames(image: Bitmap) = WallpaperFrames(image, blurFrame(image))

private fun drawScene(theme: String): Bitmap {
    val colors = when (theme) {
        "zen" -> intArrayOf(0xFF303C4F.toInt(), 0xFFA7A59B.toInt(), 0xFF505B62.toInt())
        "liquid" -> intArrayOf(0xFF233946.toInt(), 0xFF668C99.toInt(), 0xFFB1B9B1.toInt())
        "neon" -> intArrayOf(0xFF100F2D.toInt(), 0xFF49385D.toInt(), 0xFF162A41.toInt())
        else -> intArrayOf(0xFF101F24.toInt(), 0xFF627C69.toInt(), 0xFF263F37.toInt())
    }
    val bitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(0f, 0f, 1280f, 720f, colors, null, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, 1280f, 720f, paint)
    paint.shader = null
    paint.color = Color.argb(32, 235, 235, 209)
    canvas.drawCircle(865f, 225f, if (theme == "neon") 92f else 125f, paint)
    for (layer in 0..3) {
        val base = 345f + layer * 80f
        val ridge = Path().apply {
            moveTo(0f, base + 60f)
            cubicTo(200f, base - 70f, 300f, base + 90f, 580f, base - 65f)
            cubicTo(830f, base - 150f, 1010f, base + 100f, 1280f, base - 55f)
            lineTo(1280f, 720f); lineTo(0f, 720f); close()
        }
        paint.color = if (theme == "neon") Color.argb(60 + layer * 30, 13, 16, 44)
            else Color.argb(45 + layer * 35, 9, 32, 28)
        canvas.drawPath(ridge, paint)
    }
    if (theme == "forest") {
        paint.color = Color.argb(95, 5, 24, 22)
        for (index in 0..25) {
            val x = index * 57f - 40f
            val height = 100f + ((index * 73) % 130)
            val tree = Path().apply {
                moveTo(x, 690f - height); lineTo(x - 60f, 710f); lineTo(x + 60f, 710f); close()
            }
            canvas.drawPath(tree, paint)
        }
    }
    return bitmap
}

internal fun blurFrame(source: Bitmap): Bitmap {
    // Always own a mutable working bitmap, including when source is already 96 × 54.
    val tiny = Bitmap.createBitmap(96, 54, Bitmap.Config.ARGB_8888).also {
        Canvas(it).drawBitmap(source, null, Rect(0, 0, 96, 54), Paint(Paint.FILTER_BITMAP_FLAG))
    }
    val pixels = IntArray(96 * 54)
    tiny.getPixels(pixels, 0, 96, 0, 0, 96, 54)
    // Small bounded CPU convolution done once per wallpaper, never per frame.
    repeat(3) {
        val copy = pixels.copyOf()
        for (y in 0 until 54) for (x in 0 until 96) {
            var r = 0; var g = 0; var b = 0; var n = 0
            for (dy in -3..3) for (dx in -3..3) {
                val color = copy[(y + dy).coerceIn(0, 53) * 96 + (x + dx).coerceIn(0, 95)]
                r += Color.red(color); g += Color.green(color); b += Color.blue(color); n++
            }
            pixels[y * 96 + x] = Color.rgb(r / n, g / n, b / n)
        }
    }
    tiny.setPixels(pixels, 0, 96, 0, 0, 96, 54)
    val blurred = Bitmap.createScaledBitmap(tiny, 960, 540, true)
    tiny.recycle()
    return blurred
}

/** Gentler cached music blur; settings keep their original much stronger blur. */
internal fun musicBlurFrame(source:Bitmap):Bitmap {
    val width=240;val height=135
    val cropWidth=minOf(source.width,(source.height*16f/9).toInt()).coerceAtLeast(1)
    val cropHeight=minOf(source.height,(source.width*9f/16).toInt()).coerceAtLeast(1)
    val left=(source.width-cropWidth)/2;val top=(source.height-cropHeight)/2
    val small=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
    Canvas(small).drawBitmap(source,Rect(left,top,left+cropWidth,top+cropHeight),Rect(0,0,width,height),Paint(Paint.FILTER_BITMAP_FLAG))
    val pixels=IntArray(width*height);small.getPixels(pixels,0,width,0,0,width,height)
    repeat(2) {
        val previous=pixels.copyOf()
        for(y in 0 until height) for(x in 0 until width) {
            var red=0;var green=0;var blue=0
            for(dy in -3..3) for(dx in -3..3) {
                val color=previous[(y+dy).coerceIn(0,height-1)*width+(x+dx).coerceIn(0,width-1)]
                red+=Color.red(color);green+=Color.green(color);blue+=Color.blue(color)
            }
            pixels[y*width+x]=Color.rgb(red/49,green/49,blue/49)
        }
    }
    small.setPixels(pixels,0,width,0,0,width,height)
    return Bitmap.createScaledBitmap(small,960,540,true).also { small.recycle() }
}
