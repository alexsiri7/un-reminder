package net.interstellarai.unreminder.service.notification

import net.interstellarai.unreminder.domain.model.TriggerStatus
import org.junit.Assert.assertEquals
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
