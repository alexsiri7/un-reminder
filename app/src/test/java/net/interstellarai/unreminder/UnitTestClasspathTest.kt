package net.interstellarai.unreminder

import android.util.Log
import org.junit.Assert.assertThrows
import org.junit.Test

// Guards the debug/release unit-test split in app/build.gradle.kts: if layoutlib's framework
// jar returns to the debug classpath, these fail on their own instead of the suite becoming
// order-dependent again.
class UnitTestClasspathTest {

    @Test
    fun `android util Log can be called without layoutlib natives`() {
        // Reaching the end of the call is the assertion: under layoutlib this throws
        // UnsatisfiedLinkError unless a screenshot test loaded the natives first.
        Log.d("UnitTestClasspathTest", "served by the mockable android.jar")
    }

    @Test
    fun `layoutlib framework classes are absent from the plain unit-test JVM`() {
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.android.layoutlib.bridge.Bridge")
        }
    }
}
