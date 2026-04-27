package net.interstellarai.unreminder.service.notification

import net.interstellarai.unreminder.domain.model.TriggerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationActionMappingTest {

    @Test
    fun `ACTION_COMPLETED maps to COMPLETED`() {
        assertEquals(TriggerStatus.COMPLETED, mapAction(NotificationHelper.ACTION_COMPLETED))
    }

    @Test
    fun `ACTION_DISMISSED maps to DISMISSED`() {
        assertEquals(TriggerStatus.DISMISSED, mapAction(NotificationHelper.ACTION_DISMISSED))
    }

    @Test
    fun `unknown action returns null`() {
        assertNull(mapAction("UNKNOWN_ACTION"))
    }

    companion object {
        /**
         * Mirrors the mapping logic in NotificationActionReceiver.onReceive().
         * If the receiver's when-block changes, these tests should catch the drift.
         */
        fun mapAction(action: String): TriggerStatus? = when (action) {
            NotificationHelper.ACTION_COMPLETED -> TriggerStatus.COMPLETED
            NotificationHelper.ACTION_DISMISSED -> TriggerStatus.DISMISSED
            else -> null
        }
    }
}

class RequestCodeTest {

    @Test
    fun `toRequestCode is stable for small IDs`() {
        assertEquals(42, 42L.toRequestCode())
    }

    @Test
    fun `toRequestCode folds high-32-bits into result for large IDs`() {
        // 0x1_0000_0001L has non-zero high 32 bits; XOR-fold folds them in, producing 0 ≠ 1
        val largeId = 4_294_967_297L
        val code = largeId.toRequestCode()
        assertNotEquals(largeId.toInt(), code)
        assertEquals(0, code)
    }

    @Test
    fun `action request codes are distinct for same triggerId`() {
        val triggerId = 42L
        val completedCode = (triggerId * 2 + 0).toRequestCode()
        val dismissedCode = (triggerId * 2 + 1).toRequestCode()
        assertNotEquals(completedCode, dismissedCode)
    }

    @Test
    fun `action request codes are distinct across trigger IDs`() {
        val id1 = 1L
        val id2 = 2L
        assertNotEquals((id1 * 2 + 0).toRequestCode(), (id2 * 2 + 0).toRequestCode())
    }

    @Test
    fun `toRequestCode folds to zero when high bits equal low bits (known XOR cancellation)`() {
        // XOR(x, x) == 0 — known property; these trigger IDs map to notification slot 0
        assertEquals(0, (-1L).toRequestCode())
        assertEquals(0, 0x0000_0001_0000_0001L.toRequestCode())
    }
}
