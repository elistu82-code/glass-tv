package de.glasslauncher.tv.system

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class HomeRegistrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun homeAliasIsDeclaredButDisabledByDefault() {
        val component = ComponentName(context.packageName, "${context.packageName}.HomeEntry")
        val info = context.packageManager.getActivityInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS)
        assertFalse(info.enabled)
        assertTrue(info.exported)
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addCategory(Intent.CATEGORY_DEFAULT)
        val declared = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DISABLED_COMPONENTS)
        assertTrue(declared.any { it.activityInfo.name == component.className })
        assertFalse(context.packageManager.queryIntentActivities(intent, 0).any { it.activityInfo.packageName == context.packageName })
    }
    @Test fun stockHomeRecoveryUsesAnExplicitDifferentComponent() {
        val home = requireNotNull(stockHomeIntent(context))
        assertEquals(Intent.ACTION_MAIN, home.action)
        assertTrue(home.hasCategory(Intent.CATEGORY_HOME))
        assertNotEquals(context.packageName, requireNotNull(home.component).packageName)
    }
}
