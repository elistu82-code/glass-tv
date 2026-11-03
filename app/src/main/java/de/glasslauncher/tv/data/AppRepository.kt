package de.glasslauncher.tv.data

import android.util.AtomicFile
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import de.glasslauncher.tv.system.measured
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
import de.glasslauncher.tv.core.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Scoped launchable-package discovery; no exhaustive package inventory or QUERY_ALL_PACKAGES. */
class AppRepository(private val context: Context) {
    private val packages = context.packageManager

    private val revisions = java.util.concurrent.ConcurrentHashMap<String,Long>()
    fun invalidatePackage(pkg: String) { revisions[pkg] = android.os.SystemClock.uptimeMillis(); AppIcons.invalidatePackage(pkg) }
    private val snapshot = AtomicFile(File(context.cacheDir, "apps-v1.json"))

    fun discover(): List<InstalledApp> = measured("Glass.discovery") {
        val result = linkedMapOf<String, InstalledApp>()
        // Prefer TV-native entry points when a package also exposes a phone launcher activity.
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            @Suppress("DEPRECATION")
            val matches = packages.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
            for (match in matches) {
                val info = match.activityInfo ?: continue
                if (info.packageName == context.packageName || !info.enabled || !info.applicationInfo.enabled || !info.exported) continue
                if (info.packageName in result) continue
                val component = ComponentName(info.packageName, info.name)
                result[info.packageName] = InstalledApp(
                    id = component.flattenToString(),
                    packageName = info.packageName,
                    label = runCatching { match.loadLabel(packages).toString() }.getOrDefault(info.packageName),
                    iconRevision = info.applicationInfo.sourceDir.orEmpty()+":"+(revisions[info.packageName] ?: 0),
                    isSystem = info.applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                )
            }
        }
        result.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label }).also { apps ->
            runCatching {
                val rows=JsonArray()
                apps.forEach { app -> rows.add(JsonObject().apply { addProperty("id",app.id); addProperty("pkg",app.packageName); addProperty("label",app.label); addProperty("system",app.isSystem); addProperty("rev",app.iconRevision) }) }
                val bytes=rows.toString().toByteArray(Charsets.UTF_8)
                if(bytes.size<=256*1024) {
                    val stream=snapshot.startWrite()
                    try { stream.write(bytes); snapshot.finishWrite(stream) } catch(e:Exception) { snapshot.failWrite(stream); throw e }
                }
            }
        }
    }

    /** Private expendable metadata only; never imported/exported as user settings. IO caller. */
    fun cachedDiscovery(): List<InstalledApp> = runCatching {
        if(!snapshot.baseFile.exists() || snapshot.baseFile.length()>256*1024) return@runCatching emptyList()
        val rows=snapshot.openRead().bufferedReader().use { JsonParser.parseReader(it).asJsonArray }
        require(rows.size()<=512)
        rows.map { row ->
            val o=row.asJsonObject; val id=o.get("id").asString; val pkg=o.get("pkg").asString
            require(ComponentName.unflattenFromString(id)?.packageName==pkg && pkg!=context.packageName)
            InstalledApp(id,pkg,o.get("label").asString.take(256),isSystem=o.get("system").asBoolean,iconRevision=o.get("rev").asString)
        }
    }.getOrDefault(emptyList())

    fun launch(app: InstalledApp): Boolean {
        val component = ComponentName.unflattenFromString(app.id) ?: return false
        if (component.packageName != app.packageName || app.packageName == context.packageName) return false
        return start(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
    }

    /** Resolve the normal TV/phone launch intent off main; never dispatch transport/autoplay commands. */
    suspend fun launchPackage(packageName:String):Boolean {
        if(packageName==context.packageName) return false
        val intent=withContext(Dispatchers.IO) {
            runCatching { packages.getLeanbackLaunchIntentForPackage(packageName)
                ?: packages.getLaunchIntentForPackage(packageName) }.getOrNull()
        } ?: return false
        return withContext(Dispatchers.Main) { start(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun details(app: InstalledApp): Boolean = start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", app.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    fun uninstall(app: InstalledApp): Boolean {
        // Re-query current package flags, rather than trusting a possibly stale UI item.
        val system = runCatching {
            @Suppress("DEPRECATION")
            packages.getApplicationInfo(app.packageName, 0).flags and
                (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        }.getOrElse { return false }
        if (system || app.packageName == context.packageName) return false
        return start(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
