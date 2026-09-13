package net.interstellarai.unreminder.data.repository

import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import net.interstellarai.unreminder.data.db.TriggerDao
import net.interstellarai.unreminder.domain.model.NotificationStyle
import net.interstellarai.unreminder.domain.model.TriggerStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TriggerRepositoryTest {

    private lateinit var triggerDao: TriggerDao
    private lateinit var repo: TriggerRepository

    @Before
    fun setup() {
        triggerDao = mockk()
        repo = TriggerRepository(triggerDao)
    }

    @Test
    fun `updateFired freezes the chosen style by name alongside the prompt and action url`() = runTest {
        coJustRun { triggerDao.updateFired(any(), any(), any(), any(), any(), any(), any()) }

        for (style in NotificationStyle.entries) {
            repo.updateFired(id = 42L, habitId = 1L, prompt = "p", actionUrl = "https://v", style = style)

            coVerify(exactly = 1) {
                triggerDao.updateFired(
                    id = 42L,
                    status = "FIRED",
                    firedAt = any(),
                    habitId = 1L,
                    prompt = "p",
                    actionUrl = "https://v",
                    style = style.name,
                )
            }
        }
    }

    @Test
    fun `COMPLETED may replace a FIRED or an OPENED trigger`() = runTest {
        coEvery { triggerDao.updateStatusIf(42L, "COMPLETED", listOf("FIRED", "OPENED")) } returns 1

        assertTrue(repo.recordOutcome(42L, TriggerStatus.COMPLETED))
    }

    @Test
    fun `OPENED, DISMISSED and LATER may only resolve a still-FIRED trigger`() = runTest {
        coEvery { triggerDao.updateStatusIf(42L, any(), listOf("FIRED")) } returns 1

        assertTrue(repo.recordOutcome(42L, TriggerStatus.OPENED))
        assertTrue(repo.recordOutcome(42L, TriggerStatus.DISMISSED))
        assertTrue(repo.recordOutcome(42L, TriggerStatus.LATER))

        coVerify(exactly = 1) { triggerDao.updateStatusIf(42L, "OPENED", listOf("FIRED")) }
        coVerify(exactly = 1) { triggerDao.updateStatusIf(42L, "DISMISSED", listOf("FIRED")) }
        coVerify(exactly = 1) { triggerDao.updateStatusIf(42L, "LATER", listOf("FIRED")) }
    }

    @Test
    fun `a declined write reports false`() = runTest {
        coEvery { triggerDao.updateStatusIf(42L, "DISMISSED", listOf("FIRED")) } returns 0

        assertFalse(repo.recordOutcome(42L, TriggerStatus.DISMISSED))
    }

    @Test
    fun `lifecycle statuses are not user outcomes`() = runTest {
        for (status in listOf(TriggerStatus.SCHEDULED, TriggerStatus.FIRED, TriggerStatus.EXPIRED)) {
            val thrown = runCatching { repo.recordOutcome(42L, status) }.exceptionOrNull()
            assertTrue("$status should be rejected", thrown is IllegalArgumentException)
        }
        coVerify(exactly = 0) { triggerDao.updateStatusIf(any(), any(), any()) }
    }
}
