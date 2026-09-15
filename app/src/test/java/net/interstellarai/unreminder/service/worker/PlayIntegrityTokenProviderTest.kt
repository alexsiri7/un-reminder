package net.interstellarai.unreminder.service.worker

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.integrity.StandardIntegrityException
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityToken
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import com.google.android.play.core.integrity.model.StandardIntegrityErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.sentry.ScopeCallback
import io.sentry.Sentry
import io.sentry.protocol.SentryId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PlayIntegrityTokenProviderTest {

    private val manager: StandardIntegrityManager = mockk()
    private val tokenProvider: StandardIntegrityTokenProvider = mockk()

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>(), any()) } returns 0
        mockkStatic(Sentry::class)
        every { Sentry.captureException(any(), any<ScopeCallback>()) } returns SentryId.EMPTY_ID
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
        unmockkStatic(Sentry::class)
    }

    private fun provider(cloudProjectNumber: Long? = 123L) = PlayIntegrityTokenProvider(manager, cloudProjectNumber)

    private fun integrityError(code: Int): StandardIntegrityException =
        mockk(relaxed = true) { every { errorCode } returns code }

    private fun playToken(value: String): StandardIntegrityToken = mockk { every { token() } returns value }

    private fun managerPrepares() {
        every { manager.prepareIntegrityToken(any()) } returns Tasks.forResult(tokenProvider)
    }

    @Test
    fun `a build without a cloud project number never touches Play`() = runTest {
        val result = provider(cloudProjectNumber = null).token("hash")

        assertEquals(IntegrityTokenResult.Unavailable(retryable = false, errorCode = null), result)
        provider(cloudProjectNumber = null).warmUp()
        verify(exactly = 0) { manager.prepareIntegrityToken(any()) }
    }

    @Test
    fun `prepares once and passes each request hash through`() = runTest {
        managerPrepares()
        val requests = mutableListOf<StandardIntegrityTokenRequest>()
        every { tokenProvider.request(capture(requests)) } returns Tasks.forResult(playToken("t1")) andThen Tasks.forResult(playToken("t2"))
        val p = provider()

        assertEquals(IntegrityTokenResult.Token("t1"), p.token("hash-1"))
        assertEquals(IntegrityTokenResult.Token("t2"), p.token("hash-2"))

        verify(exactly = 1) { manager.prepareIntegrityToken(any()) }
        assertEquals(listOf("hash-1", "hash-2"), requests.map { it.requestHash() })
    }

    @Test
    fun `warmUp prepares so the first token request needs no preparation`() = runTest {
        managerPrepares()
        every { tokenProvider.request(any()) } returns Tasks.forResult(playToken("t1"))
        val p = provider()

        p.warmUp()
        p.token("hash")

        verify(exactly = 1) { manager.prepareIntegrityToken(any()) }
    }

    @Test
    fun `warmUp swallows a preparation failure`() = runTest {
        every { manager.prepareIntegrityToken(any()) } returns Tasks.forException(integrityError(StandardIntegrityErrorCode.NETWORK_ERROR))

        provider().warmUp()
    }

    @Test
    fun `an invalid provider is prepared again and the request retried once`() = runTest {
        managerPrepares()
        every { tokenProvider.request(any()) } returns
            Tasks.forException(integrityError(StandardIntegrityErrorCode.INTEGRITY_TOKEN_PROVIDER_INVALID)) andThen
            Tasks.forResult(playToken("fresh"))

        assertEquals(IntegrityTokenResult.Token("fresh"), provider().token("hash"))

        verify(exactly = 2) { manager.prepareIntegrityToken(any()) }
        verify(exactly = 2) { tokenProvider.request(any()) }
    }

    @Test
    fun `a provider invalid twice in a row is reported unavailable`() = runTest {
        managerPrepares()
        every { tokenProvider.request(any()) } returns
            Tasks.forException(integrityError(StandardIntegrityErrorCode.INTEGRITY_TOKEN_PROVIDER_INVALID))

        val result = provider().token("hash")

        assertEquals(
            IntegrityTokenResult.Unavailable(retryable = false, errorCode = StandardIntegrityErrorCode.INTEGRITY_TOKEN_PROVIDER_INVALID),
            result,
        )
        verify(exactly = 2) { tokenProvider.request(any()) }
    }

    @Test
    fun `a network error is retryable`() = runTest {
        managerPrepares()
        every { tokenProvider.request(any()) } returns Tasks.forException(integrityError(StandardIntegrityErrorCode.NETWORK_ERROR))

        assertEquals(
            IntegrityTokenResult.Unavailable(retryable = true, errorCode = StandardIntegrityErrorCode.NETWORK_ERROR),
            provider().token("hash"),
        )
        verify(exactly = 0) { Sentry.captureException(any(), any<ScopeCallback>()) }
    }

    @Test
    fun `missing Play Services is not retryable`() = runTest {
        every { manager.prepareIntegrityToken(any()) } returns
            Tasks.forException(integrityError(StandardIntegrityErrorCode.PLAY_SERVICES_NOT_FOUND))

        assertEquals(
            IntegrityTokenResult.Unavailable(retryable = false, errorCode = StandardIntegrityErrorCode.PLAY_SERVICES_NOT_FOUND),
            provider().token("hash"),
        )
    }

    @Test
    fun `an unexpected failure is reported to Sentry and not retryable`() = runTest {
        managerPrepares()
        every { tokenProvider.request(any()) } returns Tasks.forException(IllegalStateException("boom"))

        assertEquals(IntegrityTokenResult.Unavailable(retryable = false, errorCode = null), provider().token("hash"))
        verify(exactly = 1) { Sentry.captureException(any<IllegalStateException>(), any<ScopeCallback>()) }
    }
}
