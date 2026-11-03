package de.glasslauncher.tv.data

import de.glasslauncher.tv.core.InstalledApp
import de.glasslauncher.tv.core.LauncherConfig

object AppOrdering {
    fun visibleApps(apps: List<InstalledApp>, config: LauncherConfig): List<InstalledApp> {
        val positions = config.order.distinct().withIndex().associate { it.value to it.index }
        return apps.distinctBy { it.id }.filterNot { SystemAppPolicy.isHidden(it, config) }
            .sortedWith(compareBy<InstalledApp> { positions[it.id] ?: Int.MAX_VALUE }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }.thenBy { it.id })
    }

    fun move(order: List<String>, id: String, delta: Int): List<String> {
        val result = order.distinct().toMutableList()
        val from = result.indexOf(id)
        if (from < 0) return result
        val to = (from.toLong() + delta).coerceIn(0L, result.lastIndex.toLong()).toInt()
        result.removeAt(from)
        result.add(to, id)
        return result
    }
}
