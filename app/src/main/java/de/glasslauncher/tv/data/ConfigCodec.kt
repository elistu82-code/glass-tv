package de.glasslauncher.tv.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import de.glasslauncher.tv.core.LauncherConfig
import java.io.StringReader
import java.math.BigDecimal

/** Versioned, bounded settings. Parsing and schema validation never mutate active settings. */
object ConfigCodec {
    const val MAX_BYTES = 256 * 1024
    private const val MAX_APPS = 1000
    private val componentId = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)*/\\.?[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*")
    private val themes = setOf("forest", "zen", "liquid", "neon")
    private val easings = setOf("smooth", "linear")

    fun encode(config: LauncherConfig): String = JsonObject().apply {
        addProperty("version", 1)
        add("order", JsonArray().apply { config.order.forEach { add(it) } })
        add("pinned", JsonArray().apply { config.pinned.forEach { add(it) } })
        add("hidden", JsonArray().apply { config.hidden.forEach { add(it) } })
        add("shownSystemApps", JsonArray().apply { config.shownSystemApps.forEach { add(it) } })
        addProperty("theme", config.theme)
        addProperty("wallpaperPath", config.wallpaperPath)
        addProperty("wallpaperVideo", config.wallpaperVideo)
        addProperty("animationSpeed", config.animationSpeed.toDouble())
        addProperty("reducedMotion", config.reducedMotion)
        addProperty("glassOpacity", config.glassOpacity.toDouble())
        addProperty("easing", config.easing)
        addProperty("musicMode", config.musicMode)
        addProperty("showMusicInfo", config.showMusicInfo)
        addProperty("simulatedVisualizer", config.simulatedVisualizer)
        addProperty("uiSounds", config.uiSounds)
        addProperty("uiSoundIntensity", config.uiSoundIntensity.toDouble())
    }.toString()

    fun decode(json: String): LauncherConfig {
        require(json.length <= MAX_BYTES && json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Configuration exceeds 256 KiB" }
        val obj = parseObject(json)
        val version = obj["version"]?.let { value ->
            require(value is JsonPrimitive && value.isNumber) { "Invalid configuration version" }
            runCatching { value.asBigDecimal }.getOrElse { throw IllegalArgumentException("Invalid configuration version", it) }
        } ?: BigDecimal.ONE
        require(version.compareTo(BigDecimal.ZERO) == 0 || version.compareTo(BigDecimal.ONE) == 0) { "Unsupported configuration version" }
        val defaults = LauncherConfig()
        return LauncherConfig(
            order = ids(obj, "order"),
            pinned = ids(obj, "pinned").toSet(),
            hidden = ids(obj, "hidden").toSet(),
            shownSystemApps = ids(obj, "shownSystemApps").toSet(),
            theme = string(obj, "theme", defaults.theme).takeIf { it in themes } ?: defaults.theme,
            wallpaperPath = optionalString(obj, "wallpaperPath")?.also {
                require(it.length <= 4096 && '\u0000' !in it) { "Invalid wallpaper path" }
            },
            wallpaperVideo = bool(obj, "wallpaperVideo", false),
            animationSpeed = number(obj, "animationSpeed", 1.0).coerceIn(.5, 2.0).toFloat(),
            reducedMotion = bool(obj, "reducedMotion", false),
            glassOpacity = number(obj, "glassOpacity", .22).coerceIn(.1, .6).toFloat(),
            easing = string(obj, "easing", defaults.easing).takeIf { it in easings } ?: defaults.easing,
            musicMode = bool(obj, "musicMode", false),
            showMusicInfo = bool(obj, "showMusicInfo", true),
            simulatedVisualizer = bool(obj, "simulatedVisualizer", false),
            uiSounds = bool(obj, "uiSounds", true),
            uiSoundIntensity = number(obj, "uiSoundIntensity", .5).coerceIn(0.0, 1.0).toFloat(),
        )
    }

    /** Gson owns JSON syntax; streaming the root additionally rejects ambiguous duplicate keys. */
    private fun parseObject(json: String): JsonObject = try {
        JsonReader(StringReader(json)).use { reader ->
            reader.strictness = Strictness.STRICT
            require(reader.peek() == JsonToken.BEGIN_OBJECT) { "Configuration must be an object" }
            reader.beginObject()
            val result = JsonObject()
            while (reader.hasNext()) {
                val key = reader.nextName()
                require(!result.has(key)) { "Duplicate configuration field: $key" }
                result.add(key, JsonParser.parseReader(reader))
            }
            reader.endObject()
            require(reader.peek() == JsonToken.END_DOCUMENT) { "Unexpected trailing configuration data" }
            result
        }
    } catch (failure: IllegalArgumentException) {
        throw failure
    } catch (failure: Exception) {
        throw IllegalArgumentException("Invalid configuration JSON", failure)
    }

    private fun ids(obj: JsonObject, key: String): List<String> {
        val array = obj[key] ?: return emptyList()
        require(array is JsonArray && array.size() <= MAX_APPS) { "Invalid app list: $key" }
        return array.map { value ->
            require(value is JsonPrimitive && value.isString) { "Invalid application ID" }
            value.asString.also { require(it.length <= 512 && componentId.matches(it)) { "Invalid application ID" } }
        }.distinct()
    }

    private fun optionalString(obj: JsonObject, key: String): String? {
        val value = obj[key] ?: return null
        if (value.isJsonNull) return null
        require(value is JsonPrimitive && value.isString) { "Invalid string: $key" }
        return value.asString
    }

    private fun string(obj: JsonObject, key: String, default: String): String {
        val value = obj[key] ?: return default
        require(value is JsonPrimitive && value.isString && value.asString.length <= 128) { "Invalid string: $key" }
        return value.asString
    }

    private fun number(obj: JsonObject, key: String, default: Double): Double {
        val value = obj[key] ?: return default
        require(value is JsonPrimitive && value.isNumber) { "Invalid number: $key" }
        return value.asDouble.also { require(it.isFinite()) { "Invalid number: $key" } }
    }

    private fun bool(obj: JsonObject, key: String, default: Boolean): Boolean {
        val value = obj[key] ?: return default
        require(value is JsonPrimitive && value.isBoolean) { "Invalid boolean: $key" }
        return value.asBoolean
    }
}
