package de.glasslauncher.tv.system

import android.app.role.RoleManager
import java.io.File
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import de.glasslauncher.tv.MainActivity
import de.glasslauncher.tv.testing.AwakeDeviceRule
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Temporary AOSP-emulator HOME selection only. Never assesses or activates Fire-OS redirection. */
@OptIn(ExperimentalTestApi::class)
class HomeRoutingTest {
    @get:Rule(order = 0) val awake = AwakeDeviceRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }

    @Test fun emulatorReportsRealHomeRoutingAndChecksExplicitEntryRecovery() {
        assumeTrue("AOSP emulator only", Build.HARDWARE in setOf("ranchu", "goldfish"))
        val context = instrumentation.targetContext
        val manager = context.packageManager
        val alias = ComponentName(context.packageName, "${context.packageName}.HomeEntry")
        val oldState = manager.getComponentEnabledSetting(alias)
        val roleManager = context.getSystemService(RoleManager::class.java)
        val oldRoleHeld = roleManager.isRoleHeld(RoleManager.ROLE_HOME)
        val original = shell("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME").lineSequence().last()
        // This test only changes a known stock-TV emulator HOME and its own package.
        assumeTrue("Expected stock Android-TV HOME", original == "com.google.android.tvlauncher/.MainActivity")
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("app-row").fetchSemanticsNodes().isNotEmpty() }
        try {
            // Android TV disallows Shell's component toggle; the owning app needs no special permission.
            manager.setComponentEnabledSetting(alias, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            val selection = shell("cmd package set-home-activity --user 0 ${alias.flattenToString()}")
            val resolved = shell("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
            val candidates = shell("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
            val routesToGlass = resolved.contains("HomeEntry")
            assertTrue("Unexpected HOME resolver: $resolved", routesToGlass || resolved.endsWith(original))
            val held = context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)
            val result = File(context.cacheDir, "home-routing-result.txt")
            result.writeText("Selection: $selection\nRole held: $held\nResolved: $resolved\nPhysical HOME routes to Glass: $routesToGlass\nCandidates: $candidates\n")
            shell("am start -W -n $original")
            shell("input keyevent KEYCODE_HOME")
            val expectedPackage = if (routesToGlass) context.packageName else "com.google.android.tvlauncher"
            compose.waitUntil(5_000) {
                shell("dumpsys activity activities").lineSequence().any { "mResumedActivity:" in it && expectedPackage in it }
            }
            result.appendText("Physical HOME foreground matches resolver: true\n")
            // Explicit entry is a separate capability; never report it as a successful HOME key route.
            val explicitHome = "am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n ${alias.flattenToString()}"
            shell(explicitHome)
            compose.onNodeWithTag("app-row").assertIsDisplayed()
            compose.onRoot().performKeyInput { pressKey(Key.DirectionUp); pressKey(Key.DirectionCenter) }
            compose.onNodeWithTag("calendar-overlay").assertIsDisplayed()
            if (routesToGlass) shell("input keyevent KEYCODE_HOME") else shell(explicitHome)
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("calendar-overlay").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("app-row").assertIsDisplayed()
            result.appendText("Explicit HOME entry dismisses calendar: true\n")
            compose.onRoot().performKeyInput { pressKey(Key.Back) }
            compose.waitUntil(5_000) {
                shell("dumpsys activity activities").lineSequence().any { "mResumedActivity:" in it && "com.google.android.tvlauncher" in it }
            }
            result.appendText("Back reaches original stock HOME: true\n")
        } finally {
            try { shell("cmd package set-home-activity --user 0 $original") }
            finally { manager.setComponentEnabledSetting(alias, oldState, PackageManager.DONT_KILL_APP) }
        }
        assertTrue(shell("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME").endsWith(original))
        assertEquals(oldState, manager.getComponentEnabledSetting(alias))
        assertEquals(oldRoleHeld, roleManager.isRoleHeld(RoleManager.ROLE_HOME))
        File(context.cacheDir, "home-routing-result.txt").appendText("Original HOME and alias state restored: true\n")
    }
}
