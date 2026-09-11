package net.interstellarai.unreminder.service.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EveningInvitationWordingTest {

    @Test
    fun `consecutive days get different lines`() {
        val day = LocalDate.of(2026, 9, 11)
        assertNotEquals(EveningInvitationWording.body(day), EveningInvitationWording.body(day.plusDays(1)))
    }

    @Test
    fun `every line in the rotation is used`() {
        val start = LocalDate.of(2026, 9, 11)
        val used = (0 until EveningInvitationWording.BODIES.size).map { EveningInvitationWording.body(start.plusDays(it.toLong())) }.toSet()
        assertEquals(EveningInvitationWording.BODIES.toSet(), used)
    }

    @Test
    fun `lines are distinct and non blank`() {
        assertEquals(EveningInvitationWording.BODIES.size, EveningInvitationWording.BODIES.toSet().size)
        assertTrue(EveningInvitationWording.BODIES.all { it.isNotBlank() })
    }
}
