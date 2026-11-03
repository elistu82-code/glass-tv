package de.glasslauncher.tv.wallpaper

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class BoundedCopyTest {
    @Test fun copiesExactLimitAndRejectsOversizedStream() {
        val bytes = ByteArray(17) { it.toByte() }
        val output = ByteArrayOutputStream()
        copyBounded(ByteArrayInputStream(bytes), output, 17)
        assertArrayEquals(bytes, output.toByteArray())
        assertThrows(IllegalArgumentException::class.java) {
            copyBounded(ByteArrayInputStream(bytes), ByteArrayOutputStream(), 16)
        }
    }
    @Test fun rejectsEmptyImport() {
        assertThrows(IllegalArgumentException::class.java) {
            copyBounded(ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream(), 16)
        }
    }
}
