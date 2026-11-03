package de.glasslauncher.tv.testing

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Wake before ActivityScenario launches; never change the Fire TV's sleep/settings policies. */
class AwakeDeviceRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val command = InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("input keyevent KEYCODE_WAKEUP")
            ParcelFileDescriptor.AutoCloseInputStream(command).use { it.readBytes() }
            base.evaluate()
        }
    }
}
