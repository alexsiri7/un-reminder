package net.interstellarai.unreminder.service.worker

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.sentry.ScopeCallback
import io.sentry.Sentry
import io.sentry.protocol.SentryId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import net.interstellarai.unreminder.data.repository.GenerationFailure
import net.interstellarai.unreminder.data.repository.GenerationFailureRepository
import net.interstellarai.unreminder.data.repository.SelfRegistration
import net.interstellarai.unreminder.data.repository.WorkerTokenRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Duration
import java.time.Instant

class WorkerRegistrarTest {

    private companion object {
        const val LABEL = "Pixel 8"
        const val URL = "https://worker.example"
        val MINTED = "ur1_0123456789abcdef_" + "a".repeat(64)
        val OLDER = "ur1_fedcba9876543210_" + "b".repeat(64)
    }

    private val storedToken = MutableStateFlow("")
    private val storedRegistration = MutableStateFlow<SelfRegistration?>(null)
    private val storedRetryAt = MutableStateFlow<Instant?>(null)
    private val storedFailure = MutableStateFlow<GenerationFailure?>(null)

    private val tokenRepository: WorkerTokenRepository = mockk {
        every { token } returns storedToken
        every { selfRegistration } returns storedRegistration
        every { registrationRetryAt } returns storedRetryAt
        coEvery { setRegisteredToken(any(), any(), any()) } answers {
            storedToken.value = firstArg()
            storedRegistration.value = SelfRegistration(secondArg(), thirdArg())
            storedRetryAt.value = null
        }
        coEvery { clearIfCurrent(any()) } answers {
            (storedToken.value == firstArg<String>()).also { matched ->
                if (matched) {
                    storedToken.value = ""
                    storedRegistration.value = null
                }
            }
        }
        coEvery { deferRegistration(any()) } answers { storedRetryAt.value = firstArg() }
    }

    private val failureRepository: GenerationFailureRepository = mockk {
        every { failure } returns storedFailure
        coEvery { record(any()) } answers { storedFailure.value = firstArg() }
        coEvery { clear() } answers { storedFailure.value = null }
    }

    private val proxy: RequestyProxyClient = mockk()

    private fun registrar(workerUrl: String = URL) =
        WorkerRegistrar(proxy, tokenRepository, failureRepository, workerUrl, LABEL)

    private fun proxyReturns(minted: String = MINTED) {
        coEvery { proxy.register(any(), any()) } returns Registration(minted)
    }

    @Before
    fun setup() {
        mockkStatic(Sentry::class)
        every { Sentry.captureException(any(), any<ScopeCallback>()) } returns SentryId.EMPTY_ID
    }

    @After
    fun tearDown() {
        unmockkStatic(Sentry::class)
    }

    // --- success ---

    @Test
    fun `ensureToken registers with the device label and stores the token as self-registered`() = runTest {
        proxyReturns()

        assertEquals(MINTED, registrar().ensureToken())

        coVerify(exactly = 1) { proxy.register(LABEL, URL) }
        coVerify(exactly = 1) { tokenRepository.setRegisteredToken(MINTED, LABEL, any()) }
        assertEquals(LABEL, storedRegistration.value?.deviceLabel)
    }

    @Test
    fun `registering clears a token, registration or service failure`() = runTest {
        for (kind in listOf(
            GenerationFailure.Kind.TOKEN_REJECTED,
            GenerationFailure.Kind.INTEGRITY_UNAVAILABLE,
            GenerationFailure.Kind.REGISTRATION_REJECTED,
            GenerationFailure.Kind.REGISTRATION_CAP,
            GenerationFailure.Kind.SERVICE_UNAVAILABLE,
        )) {
            storedToken.value = ""
            storedFailure.value = GenerationFailure(kind, null, Instant.EPOCH)
            proxyReturns()

            registrar().ensureToken()

            assertNull("$kind should be cleared", storedFailure.value)
        }
    }

    @Test
    fun `registering keeps a spend-cap failure`() = runTest {
        val spendCap = GenerationFailure(GenerationFailure.Kind.SPEND_CAP_USER, null, Instant.EPOCH)
        storedFailure.value = spendCap
        proxyReturns()

        registrar().ensureToken()

        assertEquals(spendCap, storedFailure.value)
    }

    @Test
    fun `ensureToken returns a stored token without registering`() = runTest {
        storedToken.value = OLDER

        assertEquals(OLDER, registrar().ensureToken())

        coVerify(exactly = 0) { proxy.register(any(), any()) }
    }

    // --- failures ---

    private suspend fun assertFailure(thrown: Exception, kind: GenerationFailure.Kind, backoff: Duration) {
        storedToken.value = ""
        storedRetryAt.value = null
        storedFailure.value = null
        coEvery { proxy.register(any(), any()) } throws thrown
        val before = Instant.now()

        assertEquals("", registrar().ensureToken())

        assertEquals(kind, storedFailure.value?.kind)
        val deferred = checkNotNull(storedRetryAt.value) { "no backoff after $thrown" }
        assertFalse(deferred.isBefore(before.plus(backoff)))
        assertFalse(deferred.isAfter(Instant.now().plus(backoff)))
    }

    @Test
    fun `expected failures are recorded, deferred, and not reported`() = runTest {
        assertFailure(IntegrityUnavailableException(retryable = false), GenerationFailure.Kind.INTEGRITY_UNAVAILABLE, WorkerRegistrar.REFUSAL_BACKOFF)
        assertFailure(IntegrityUnavailableException(retryable = true), GenerationFailure.Kind.SERVICE_UNAVAILABLE, WorkerRegistrar.TRANSIENT_BACKOFF)
        assertFailure(WorkerIntegrityException("unrecognized-app", retryable = false), GenerationFailure.Kind.REGISTRATION_REJECTED, WorkerRegistrar.REFUSAL_BACKOFF)
        assertFailure(RegistrationCapException(), GenerationFailure.Kind.REGISTRATION_CAP, WorkerRegistrar.REFUSAL_BACKOFF)
        assertFailure(WorkerError(503, "Registration unavailable"), GenerationFailure.Kind.SERVICE_UNAVAILABLE, WorkerRegistrar.TRANSIENT_BACKOFF)
        assertFailure(WorkerError(429, """{"error":"Rate limit exceeded"}"""), GenerationFailure.Kind.SERVICE_UNAVAILABLE, WorkerRegistrar.TRANSIENT_BACKOFF)
        assertFailure(IOException("offline"), GenerationFailure.Kind.SERVICE_UNAVAILABLE, WorkerRegistrar.TRANSIENT_BACKOFF)

        verify(exactly = 0) { Sentry.captureException(any(), any<ScopeCallback>()) }
    }

    @Test
    fun `an unexpected client error is reported and backs off like a refusal`() = runTest {
        assertFailure(WorkerError(400, "bad label"), GenerationFailure.Kind.SERVICE_UNAVAILABLE, WorkerRegistrar.REFUSAL_BACKOFF)

        verify(exactly = 1) { Sentry.captureException(any(), any<ScopeCallback>()) }
    }

    @Test
    fun `a malformed token is reported and never stored`() = runTest {
        proxyReturns(minted = "not-a-token")

        assertEquals("", registrar().ensureToken())

        coVerify(exactly = 0) { tokenRepository.setRegisteredToken(any(), any(), any()) }
        assertEquals(GenerationFailure.Kind.SERVICE_UNAVAILABLE, storedFailure.value?.kind)
        verify(exactly = 1) { Sentry.captureException(any(), any<ScopeCallback>()) }
    }

    // --- backoff ---

    @Test
    fun `a pending backoff skips background registration`() = runTest {
        storedRetryAt.value = Instant.now().plus(Duration.ofMinutes(5))

        assertEquals("", registrar().ensureToken())

        coVerify(exactly = 0) { proxy.register(any(), any()) }
    }

    @Test
    fun `ignoreBackoff registers despite a pending backoff`() = runTest {
        storedRetryAt.value = Instant.now().plus(Duration.ofMinutes(5))
        proxyReturns()

        assertEquals(MINTED, registrar().ensureToken(ignoreBackoff = true))
    }

    @Test
    fun `an elapsed backoff lets background registration through`() = runTest {
        storedRetryAt.value = Instant.now().minusSeconds(1)
        proxyReturns()

        assertEquals(MINTED, registrar().ensureToken())
    }

    @Test
    fun `register ignores the backoff and replaces a stored token`() = runTest {
        storedToken.value = OLDER
        storedRetryAt.value = Instant.now().plus(Duration.ofHours(1))
        proxyReturns()

        val outcome = registrar().register()

        assertEquals(RegistrationOutcome.Registered(WorkerToken.displayId(MINTED)), outcome)
        assertEquals(MINTED, storedToken.value)
    }

    @Test
    fun `register reports the failure kind`() = runTest {
        coEvery { proxy.register(any(), any()) } throws RegistrationCapException()

        assertEquals(RegistrationOutcome.Failed(GenerationFailure.Kind.REGISTRATION_CAP), registrar().register())
    }

    // --- no Worker ---

    @Test
    fun `a build without a Worker never registers or records anything`() = runTest {
        assertEquals("", registrar(workerUrl = "").ensureToken(ignoreBackoff = true))
        assertEquals(
            RegistrationOutcome.Failed(GenerationFailure.Kind.SERVICE_UNAVAILABLE),
            registrar(workerUrl = "").register(),
        )

        coVerify(exactly = 0) { proxy.register(any(), any()) }
        coVerify(exactly = 0) { failureRepository.record(any()) }
        coVerify(exactly = 0) { tokenRepository.deferRegistration(any()) }
    }

    // --- discardRejected ---

    private fun storedSelfRegistered(at: Instant) {
        storedToken.value = MINTED
        storedRegistration.value = SelfRegistration(LABEL, at)
    }

    @Test
    fun `discardRejected keeps a pasted token`() = runTest {
        storedToken.value = MINTED

        assertFalse(registrar().discardRejected(MINTED))

        assertEquals(MINTED, storedToken.value)
    }

    @Test
    fun `discardRejected keeps a token registered within the grace period`() = runTest {
        storedSelfRegistered(Instant.now().minus(Duration.ofMinutes(10)))

        assertFalse(registrar().discardRejected(MINTED))

        assertEquals(MINTED, storedToken.value)
    }

    @Test
    fun `discardRejected keeps a newer token than the one rejected`() = runTest {
        storedSelfRegistered(Instant.now().minus(Duration.ofHours(2)))

        assertFalse(registrar().discardRejected(OLDER))

        assertEquals(MINTED, storedToken.value)
    }

    @Test
    fun `discardRejected clears an old self-registered token`() = runTest {
        storedSelfRegistered(Instant.now().minus(Duration.ofHours(2)))

        assertTrue(registrar().discardRejected(MINTED))

        assertEquals("", storedToken.value)
    }

    @Test
    fun `discardRejected reports nothing cleared when the store cannot be written`() = runTest {
        storedSelfRegistered(Instant.now().minus(Duration.ofHours(2)))
        coEvery { tokenRepository.clearIfCurrent(any()) } throws IOException("disk full")

        assertFalse(registrar().discardRejected(MINTED))
    }

    // --- concurrency ---

    @Test
    fun `concurrent ensureToken calls register once`() = runTest {
        val release = CompletableDeferred<Unit>()
        coEvery { proxy.register(any(), any()) } coAnswers {
            release.await()
            Registration(MINTED)
        }
        val registrar = registrar()

        val first = async { registrar.ensureToken() }
        val second = async { registrar.ensureToken() }
        testScheduler.advanceUntilIdle()
        release.complete(Unit)

        assertEquals(MINTED, first.await())
        assertEquals(MINTED, second.await())
        coVerify(exactly = 1) { proxy.register(any(), any()) }
    }

    // --- deviceLabel ---

    @Test
    fun `deviceLabel prefixes the maker unless the model already carries it`() {
        assertEquals("samsung SM-S911B", deviceLabel("samsung", "SM-S911B"))
        assertEquals("Google Pixel 8", deviceLabel("Google", "Pixel 8"))
        assertEquals("oneplus 12", deviceLabel("OnePlus", "oneplus 12"))
    }

    @Test
    fun `deviceLabel collapses whitespace and control characters`() {
        assertEquals("Acme Phone X", deviceLabel("  Acme ", "Phone\t\n  X\u0000"))
    }

    @Test
    fun `deviceLabel caps at 40 code points without splitting a surrogate pair`() {
        val label = deviceLabel("", "😀".repeat(45))

        assertEquals(40, label.codePointCount(0, label.length))
        assertEquals("😀".repeat(40), label)
    }

    @Test
    fun `deviceLabel falls back when there is nothing to say`() {
        assertEquals("android", deviceLabel(null, null))
        assertEquals("android", deviceLabel(" ", "​"))
    }
}
