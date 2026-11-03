package de.glasslauncher.tv.system

import android.os.SystemClock
import android.os.Trace
import android.util.Log
import de.glasslauncher.tv.BuildConfig

internal inline fun <T> measured(section: String, block: () -> T): T {
    val start=SystemClock.elapsedRealtime()
    Trace.beginSection(section)
    try { return block() } finally {
        Trace.endSection()
        if (BuildConfig.DEBUG) Log.i("GlassPerf", "$section ${SystemClock.elapsedRealtime()-start}ms")
    }
}
