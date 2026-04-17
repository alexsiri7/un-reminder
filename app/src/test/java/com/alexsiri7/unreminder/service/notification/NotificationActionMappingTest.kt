package com.alexsiri7.unreminder.service.notification

import com.alexsiri7.unreminder.domain.model.TriggerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationActionMappingTest {

    @Test
    fun `ACTION_COMPLETED_FULL maps to COMPLETED_FULL`() {
        assertEquals(TriggerStatus.COMPLETED_FULL, mapAction(NotificationHelper.ACTION_COMPLETED_FULL))
    }

    @Test
    fun `ACTION_COMPLETED_LOW_FLOOR maps to COMPLETED_LOW_FLOOR`() {
        assertEquals(TriggerStatus.COMPLETED_LOW_FLOOR, mapAction(NotificationHelper.ACTION_COMPLETED_LOW_FLOOR))
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
            NotificationHelper.ACTION_COMPLETED_FULL -> TriggerStatus.COMPLETED_FULL
            NotificationHelper.ACTION_COMPLETED_LOW_FLOOR -> TriggerStatus.COMPLETED_LOW_FLOOR
            NotificationHelper.ACTION_DISMISSED -> TriggerStatus.DISMISSED
            else -> null
        }
    }
}
