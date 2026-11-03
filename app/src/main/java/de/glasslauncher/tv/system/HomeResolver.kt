package de.glasslauncher.tv.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** Preserve a route to another HOME even when Glass is temporarily selected as default. */
internal fun stockHomeIntent(context: Context): Intent? {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val manager = context.packageManager
    val homes = manager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).filter {
        val info = it.activityInfo
        info != null && info.packageName != context.packageName && info.enabled && info.exported
    }
    val resolved = manager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
    val chosen = homes.firstOrNull { it.activityInfo.packageName == resolved?.packageName && it.activityInfo.name == resolved.name }
        ?: homes.maxByOrNull { it.priority } ?: return null
    return intent.setComponent(ComponentName(chosen.activityInfo.packageName, chosen.activityInfo.name))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
