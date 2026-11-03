package de.glasslauncher.tv.wallpaper

import java.io.InputStream
import java.io.OutputStream

fun copyBounded(input: InputStream, output: OutputStream, maxBytes: Long) {
    val buffer = ByteArray(16 * 1024)
    var total = 0L
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        require(total <= maxBytes) { "Datei ist zu groß" }
        output.write(buffer, 0, count)
    }
    require(total > 0) { "Datei ist leer" }
}
