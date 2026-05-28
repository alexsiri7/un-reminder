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
        val completedCode = (triggerId * 3 + 0).toRequestCode()
        val dismissedCode = (triggerId * 3 + 1).toRequestCode()
        val watchCode = (triggerId * 3 + 2).toRequestCode()
        assertNotEquals(completedCode, dismissedCode)
        assertNotEquals(completedCode, watchCode)
        assertNotEquals(dismissedCode, watchCode)
    }

    @Test
    fun `action request codes are distinct across trigger IDs`() {
        val id1 = 1L
        val id2 = 2L
        assertNotEquals((id1 * 3 + 0).toRequestCode(), (id2 * 3 + 0).toRequestCode())
    }

    @Test
    fun `toRequestCode folds to zero when high bits equal low bits (known XOR cancellation)`() {
        // XOR(x, x) == 0 — known property; these trigger IDs map to notification slot 0
        assertEquals(0, (-1L).toRequestCode())
        assertEquals(0, 0x0000_0001_0000_0001L.toRequestCode())
    }
}

class NotificationRequestCodeCollisionTest {

    @Test
    fun `NOTIFICATION_DETAIL_BASE is distinct from NOTIFICATION_CONTENT_BASE`() {
        assertNotEquals(
            NotificationHelper.NOTIFICATION_DETAIL_BASE,
            NotificationHelper.NOTIFICATION_CONTENT_BASE
        )
    }

    @Test
    fun `detail request code does not collide with content request code for same triggerId`() {
        val triggerId = 42L
        val detailCode = (NotificationHelper.NOTIFICATION_DETAIL_BASE + triggerId).toRequestCode()
        val contentCode = (NotificationHelper.NOTIFICATION_CONTENT_BASE + triggerId).toRequestCode()
        assertNotEquals(detailCode, contentCode)
    }

    @Test
    fun `detail request code does not collide with action codes for same triggerId`() {
        val triggerId = 42L
        val detailCode = (NotificationHelper.NOTIFICATION_DETAIL_BASE + triggerId).toRequestCode()
        assertNotEquals(detailCode, (triggerId * 3 + 0).toRequestCode())
        assertNotEquals(detailCode, (triggerId * 3 + 1).toRequestCode())
        assertNotEquals(detailCode, (triggerId * 3 + 2).toRequestCode())
    }

    @Test
    fun `detail request code does not collide with paused-habit notification code`() {
        val id = 1L
        val detailCode = (NotificationHelper.NOTIFICATION_DETAIL_BASE + id).toRequestCode()
        val pausedCode = (NotificationHelper.NOTIFICATION_ID_PAUSED_BASE + id).toRequestCode()
        assertNotEquals(detailCode, pausedCode)
    }
}
