package net.interstellarai.unreminder.ui.location

import net.interstellarai.unreminder.service.geofence.Reconciliation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecalculationStateTest {

    private val outcomes = listOf(
        Reconciliation.Reconciled,
        Reconciliation.Skipped,
        Reconciliation.PermissionMissing,
        Reconciliation.LocationDisabled,
        Reconciliation.NoFix,
        Reconciliation.Failed(IllegalStateException("boom")),
    )

    @Test
    fun `a reconciliation that succeeded needs no banner`() {
        assertEquals(RecalculationState.Idle, RecalculationState.of(Reconciliation.Reconciled))
    }

    @Test
    fun `every other outcome names a reason and what to do about it`() {
        outcomes.filter { it != Reconciliation.Reconciled }.forEach { outcome ->
            val state = RecalculationState.of(outcome)
            assertTrue("$outcome should fail", state is RecalculationState.Failed)
            state as RecalculationState.Failed
            assertTrue("$outcome has no label", state.label.isNotBlank())
            assertTrue("$outcome has no advice", state.advice.isNotBlank())
        }
    }

    @Test
    fun `no two outcomes share a reason`() {
        val labels = outcomes.filter { it != Reconciliation.Reconciled }
            .map { (RecalculationState.of(it) as RecalculationState.Failed).label }
        assertEquals(labels.size, labels.toSet().size)
    }
}
