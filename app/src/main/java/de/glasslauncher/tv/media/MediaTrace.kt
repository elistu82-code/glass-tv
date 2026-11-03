package de.glasslauncher.tv.media

import android.util.Log
import de.glasslauncher.tv.BuildConfig

/** Fixed diagnostic fields only: no titles, URIs, session tokens or notification data. */
internal object MediaTrace {
    private val previous=java.util.concurrent.ConcurrentHashMap<String,String>()
    fun distinct(phase:String,fields:String) {
        if(BuildConfig.DEBUG && previous.put(phase,fields)!=fields) event(phase,fields)
    }
    fun event(phase: String, fields: String = "") {
        if (BuildConfig.DEBUG) Log.i("GlassMedia", "$phase $fields")
    }
}
