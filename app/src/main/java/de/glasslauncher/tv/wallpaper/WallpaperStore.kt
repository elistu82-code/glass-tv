package de.glasslauncher.tv.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImportedWallpaper(val path: String, val video: Boolean)

class WallpaperStore(private val context: Context) {
    private val directory get() = File(context.filesDir, "wallpapers").apply { mkdirs() }

    suspend fun import(uri: Uri): ImportedWallpaper = withContext(Dispatchers.IO) {
        val type = context.contentResolver.getType(uri).orEmpty()
        val video = type == "video/mp4"
        require(video || type in setOf("image/jpeg", "image/png", "image/webp")) {
            "Bitte JPG, PNG, WebP oder MP4 auswählen"
        }
        val temporary = File(directory, "import-${UUID.randomUUID()}.tmp")
        val result = File(directory, "wallpaper-${UUID.randomUUID()}.${if (video) "mp4" else "jpg"}")
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Datei nicht lesbar" }
                temporary.outputStream().use { output -> copyBounded(input, output, if (video) 128L * 1024 * 1024 else 32L * 1024 * 1024) }
            }
            if (video) {
                temporary.inputStream().use { stream ->
                    val header = ByteArray(12)
                    require(stream.read(header) == 12 && String(header, 4, 4, Charsets.US_ASCII) == "ftyp") { "Ungültige MP4-Datei" }
                }
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(temporary.path)
                    val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    require(width in 1..1920 && height in 1..1080) { "Video maximal 1920 × 1080" }
                    val frame = retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 960, 540)
                    requireNotNull(frame) { "Kein lesbares Videobild" }
                    try { File(result.path + ".jpg").outputStream().use { require(frame.compress(Bitmap.CompressFormat.JPEG, 85, it)) } }
                    finally { frame.recycle() }
                } finally { retriever.release() }
                check(temporary.renameTo(result)) { "Video konnte nicht gespeichert werden" }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(temporary.path, bounds)
                require(bounds.outWidth in 1..16384 && bounds.outHeight in 1..16384 &&
                    bounds.outWidth.toLong() * bounds.outHeight <= 80_000_000) { "Ungültiges oder zu großes Bild" }
                var sample = 1
                while (bounds.outWidth / sample > 1920 || bounds.outHeight / sample > 1080) sample *= 2
                val bitmap = requireNotNull(BitmapFactory.decodeFile(temporary.path, BitmapFactory.Options().apply { inSampleSize = sample })) { "Bild nicht lesbar" }
                try { result.outputStream().use { require(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) } }
                finally { bitmap.recycle() }
            }
            ImportedWallpaper(result.path, video)
        } catch (e: Exception) {
            result.delete()
            File(result.path + ".jpg").delete()
            throw e
        } finally { temporary.delete() }
    }

    suspend fun prune(keep: String?) = withContext(Dispatchers.IO) {
        directory.listFiles()?.filter { it.path != keep && it.path != "$keep.jpg" }?.forEach { it.delete() }
    }
}
