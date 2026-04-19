package net.interstellarai.unreminder.service.sentry

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchSmokeTestTest {

    /** In-memory fake implementation of SmokeStore for tests. */
    private class FakeStore(initial: Int = -1) : LaunchSmokeTest.SmokeStore {
        var stored: Int = initial
        override fun lastReportedVersionCode(): Int = stored
        override fun setLastReportedVersionCode(value: Int) {
            stored = value
        }
    }

    private val ctx: Context = mockk(relaxed = true)

    @Test
    fun `fires on first run for a versionCode`() {
        val store = FakeStore(initial = -1)
        val captured = mutableListOf<String>()

        val fired = LaunchSmokeTest.maybeFire(
            context = ctx,
            versionName = "1.2.3",
            versionCode = 42,
            capture = { captured.add(it) },
            store = store
        )

        assertTrue(fired)
        assertEquals(listOf("launch-smoke v1.2.3+42"), captured)
        assertEquals(42, store.stored)
    }

    @Test
    fun `does not fire twice for the same versionCode`() {
        val store = FakeStore(initial = -1)
        val captured = mutableListOf<String>()

        LaunchSmokeTest.maybeFire(ctx, "1.2.3", 42, { captured.add(it) }, store)
        val secondFired = LaunchSmokeTest.maybeFire(ctx, "1.2.3", 42, { captured.add(it) }, store)

        assertFalse(secondFired)
        assertEquals(1, captured.size)
    }

    @Test
    fun `fires again after versionCode bumps`() {
        val store = FakeStore(initial = -1)
        val captured = mutableListOf<String>()

        LaunchSmokeTest.maybeFire(ctx, "1.0.0", 1, { captured.add(it) }, store)
        LaunchSmokeTest.maybeFire(ctx, "1.0.0", 1, { captured.add(it) }, store)
        val upgradeFired = LaunchSmokeTest.maybeFire(ctx, "1.0.1", 2, { captured.add(it) }, store)

        assertTrue(upgradeFired)
        assertEquals(
            listOf("launch-smoke v1.0.0+1", "launch-smoke v1.0.1+2"),
            captured
        )
        assertEquals(2, store.stored)
    }

    @Test
    fun `does not fire when store already has matching versionCode`() {
        val store = FakeStore(initial = 7)
        val captured = mutableListOf<String>()

        val fired = LaunchSmokeTest.maybeFire(
            context = ctx,
            versionName = "2.0.0",
            versionCode = 7,
            capture = { captured.add(it) },
            store = store
        )

        assertFalse(fired)
        assertTrue(captured.isEmpty())
    }
}
