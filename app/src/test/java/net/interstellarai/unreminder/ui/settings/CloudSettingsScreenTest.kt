package net.interstellarai.unreminder.ui.settings

import net.interstellarai.unreminder.data.repository.GenerationFailure
import net.interstellarai.unreminder.data.repository.GenerationFailure.Kind
import net.interstellarai.unreminder.domain.model.SpendCapType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class CloudSettingsScreenTest {

    @Test
    fun `regenerating label counts settled works against the total`() {
        assertEquals(
            "regenerating… 0 of 3",
            regeneratingLabel(RegenerationProgress(total = 3, done = 0, failed = 0)),
        )
        assertEquals(
            "regenerating… 2 of 3",
            regeneratingLabel(RegenerationProgress(total = 3, done = 2, failed = 0)),
        )
    }

    @Test
    fun `regenerating label appends the failed count only when something failed`() {
        assertEquals(
            "regenerating… 2 of 3 (1 failed)",
            regeneratingLabel(RegenerationProgress(total = 3, done = 1, failed = 1)),
        )
    }

    @Test
    fun `the token status names the device only for a registered token`() {
        assertEquals("no token yet — tap re-register", tokenStatus(null, null))
        assertEquals("token: ur1_0123456789abcdef", tokenStatus("ur1_0123456789abcdef", null))
        assertEquals(
            "registered as ur1_0123456789abcdef on Pixel 8",
            tokenStatus("ur1_0123456789abcdef", "Pixel 8"),
        )
    }

    private fun failure(kind: Kind, capType: SpendCapType? = null) =
        GenerationFailure(kind, capType, Instant.EPOCH)

    @Test
    fun `a rejected token says how to replace it`() {
        assertEquals(
            "token rejected — tap re-register, or paste a new one under advanced",
            failureMessage(failure(Kind.TOKEN_REJECTED)),
        )
    }

    @Test
    fun `the user's own cap says when it resets`() {
        assertEquals(
            "your generation budget for today is spent — it resets tomorrow",
            failureMessage(failure(Kind.SPEND_CAP_USER, SpendCapType.DAILY)),
        )
        assertEquals(
            "your generation budget for this month is spent — it resets next month",
            failureMessage(failure(Kind.SPEND_CAP_USER, SpendCapType.MONTHLY)),
        )
        assertEquals(
            "your generation budget is spent — try again later",
            failureMessage(failure(Kind.SPEND_CAP_USER)),
        )
    }

    @Test
    fun `the service's cap says it is not the user's`() {
        assertEquals(
            "the service's budget for today is spent, not yours — try tomorrow",
            failureMessage(failure(Kind.SPEND_CAP_GLOBAL, SpendCapType.DAILY)),
        )
        assertEquals(
            "the service's budget for this month is spent, not yours — try next month",
            failureMessage(failure(Kind.SPEND_CAP_GLOBAL, SpendCapType.MONTHLY)),
        )
        assertEquals(
            "the service's budget is spent, not yours — try again later",
            failureMessage(failure(Kind.SPEND_CAP_GLOBAL)),
        )
    }

    @Test
    fun `an unreachable service says the app retries on its own`() {
        assertEquals(
            "the service could not be reached — the app will try again on its own",
            failureMessage(failure(Kind.SERVICE_UNAVAILABLE)),
        )
    }

    @Test
    fun `an install Play Integrity cannot vouch for points at Play or a pasted token`() {
        assertEquals(
            "this device can't prove it's a Play install — install from Play, or paste a token under advanced",
            failureMessage(failure(Kind.INTEGRITY_UNAVAILABLE)),
        )
    }

    @Test
    fun `a build without Play Integrity support says so instead of blaming the install`() {
        assertEquals(
            "this build was made without Play Integrity support — paste a token under advanced",
            failureMessage(failure(Kind.INTEGRITY_NOT_CONFIGURED)),
        )
    }

    @Test
    fun `a rejected registration points at Play or a pasted token`() {
        assertEquals(
            "Play didn't verify this install — install from Play, or paste a token under advanced",
            failureMessage(failure(Kind.REGISTRATION_REJECTED)),
        )
    }

    @Test
    fun `the registration cap says the app retries later`() {
        assertEquals(
            "too many new installs today — the app will try again later",
            failureMessage(failure(Kind.REGISTRATION_CAP)),
        )
    }

    @Test
    fun `only token and registration kinds are token problems`() {
        assertEquals(
            setOf(Kind.TOKEN_REJECTED, Kind.INTEGRITY_UNAVAILABLE, Kind.INTEGRITY_NOT_CONFIGURED, Kind.REGISTRATION_REJECTED, Kind.REGISTRATION_CAP),
            Kind.entries.filter { it.isTokenProblem }.toSet(),
        )
    }

    @Test
    fun `the failure timestamp is rendered in the given zone`() {
        assertEquals(
            "last generation failed Mar 5 · 14:07",
            failureTimestamp(Instant.parse("2026-03-05T14:07:00Z"), ZoneId.of("UTC")),
        )
    }
}
