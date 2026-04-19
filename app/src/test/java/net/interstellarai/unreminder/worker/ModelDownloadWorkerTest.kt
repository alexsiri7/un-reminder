package net.interstellarai.unreminder.worker

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import net.interstellarai.unreminder.data.repository.ModelDownloadProgressRepository
import net.interstellarai.unreminder.service.llm.ModelCatalog
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

class ModelDownloadWorkerTest {

    private val mockContext: Context = mockk(relaxed = true)
    private val mockWorkerParams: WorkerParameters = mockk(relaxed = true)
    private val mockOkHttpClient: OkHttpClient = mockk()
    private val mockProgressRepository: ModelDownloadProgressRepository = mockk(relaxed = true)

    private lateinit var filesDir: File
    private lateinit var worker: ModelDownloadWorker

    /**
     * The worker now resolves URL + filename + magic from [ModelCatalog] using
     * the `modelId` work-data key. Use Gemma 4 as the default test target
     * because it has a non-placeholder URL (the worker short-circuits
     * placeholder URLs to Result.failure() early).
     */
    private val defaultModel = ModelCatalog.gemma4E2BLitertlm
    private val defaultFilename = defaultModel.fileName

    // 8-byte LITERTLM magic prefix + arbitrary padding to form a believable
    // model-file body in happy-path tests. The worker verifies size (via
    // Content-Length match) and magic before renaming tmp → final.
    private val litertlmMagic = byteArrayOf(0x4C, 0x49, 0x54, 0x45, 0x52, 0x54, 0x4C, 0x4D)
    private val validModelBytes = litertlmMagic + "-fake-model-payload".toByteArray()

    /** Build inputData containing [KEY_MODEL_ID] → [id]. */
    private fun inputDataFor(id: String): Data =
        Data.Builder().putString(ModelDownloadWorker.KEY_MODEL_ID, id).build()

    @Before
    fun setup() {
        filesDir = createTempDir()
        every { mockContext.filesDir } returns filesDir
        every { mockWorkerParams.runAttemptCount } returns 0
        every { mockWorkerParams.inputData } returns inputDataFor(defaultModel.id)
        // `mockk(relaxed = true)` returns default values for non-suspend calls
        // but still throws for suspend functions — stub those explicitly so the
        // worker's `progressRepository.write(...)` / `clear()` calls don't
        // nondeterministically blow up the tests.
        coJustRun { mockProgressRepository.write(any()) }
        coJustRun { mockProgressRepository.clear() }
        coEvery { mockProgressRepository.peek() } returns null
        worker = ModelDownloadWorker(
            mockContext,
            mockWorkerParams,
            mockOkHttpClient,
            mockProgressRepository,
        )
    }

    @After
    fun teardown() {
        filesDir.deleteRecursively()
    }

    // --- idempotency ---

    @Test
    fun `doWork returns success immediately when valid model file already exists`() = runTest {
        // Existing file with valid LITERTLM magic bytes passes the pre-download
        // integrity check and short-circuits without hitting the network.
        File(filesDir, defaultFilename).writeBytes(validModelBytes)

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        verify { mockOkHttpClient wasNot Called }
    }

    // --- happy path ---

    @Test
    fun `doWork writes file atomically and returns success`() = runTest {
        val mockCall: Call = mockk()
        val mockResponse: Response = mockk()
        val mockBody: ResponseBody = mockk()
        every { mockOkHttpClient.newCall(any<Request>()) } returns mockCall
        every { mockCall.execute() } returns mockResponse
        every { mockResponse.isSuccessful } returns true
        every { mockResponse.code } returns 200
        every { mockResponse.body } returns mockBody
        every { mockBody.contentLength() } returns validModelBytes.size.toLong()
        every { mockBody.byteStream() } returns validModelBytes.inputStream()
        every { mockBody.close() } just Runs
        every { mockResponse.close() } just Runs

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        assertTrue(File(filesDir, defaultFilename).exists())
        assertFalse(File(filesDir, "$defaultFilename.tmp").exists())
    }

    // --- size mismatch -> retry + cleanup ---

    @Test
    fun `doWork returns retry and keeps partial tmp when body is shorter than Content-Length`() = runTest {
        // Regression guard for the field bug: server promises 100 bytes, only
        // delivers 50 — stream ends without throwing, but the on-disk file is
        // truncated. Worker must detect this and NOT rename to the final name.
        //
        // New resume-from-partial semantics (fix for the backgrounded-download
        // bug): when the received body is SHORTER than declared, we preserve
        // the partial tmp file so the next WorkManager retry can send a
        // `Range: bytes=N-` header and finish the download from where this
        // attempt stopped. Only deletions that would lose recoverable bytes
        // were removed; magic-byte mismatch still deletes (HTML error body is
        // not a resumable download).
        val content = litertlmMagic + ByteArray(42) // 50 bytes total
        val declaredLength = 100L
        val mockCall: Call = mockk()
        val mockResponse: Response = mockk()
        val mockBody: ResponseBody = mockk()
        every { mockOkHttpClient.newCall(any<Request>()) } returns mockCall
        every { mockCall.execute() } returns mockResponse
        every { mockResponse.isSuccessful } returns true
        every { mockResponse.code } returns 200
        every { mockResponse.body } returns mockBody
        every { mockBody.contentLength() } returns declaredLength
        every { mockBody.byteStream() } returns content.inputStream()
        every { mockBody.close() } just Runs
        every { mockResponse.close() } just Runs

        val result = worker.doWork()

        assertEquals(Result.retry(), result)
        val tmp = File(filesDir, "$defaultFilename.tmp")
        assertTrue(
            "tmp file should be retained so resume-from-partial can continue",
            tmp.exists(),
        )
        assertEquals(
            "tmp should contain exactly the partial bytes received (50)",
            50L,
            tmp.length(),
        )
        assertFalse(
            "model file should NOT exist when the download was truncated",
            File(filesDir, defaultFilename).exists(),
        )
    }

    // --- bad magic bytes (HTML error body served as 200) -> retry + cleanup ---

    @Test
    fun `doWork returns retry and deletes tmp when body has wrong magic bytes`() = runTest {
        // Regression guard: a misconfigured CDN / Cloudflare-interstitial can
        // return HTTP 200 with `<!DOCTYPE html>...` which would sail through
        // the stream copy but LiteRT-LM cannot parse.
        val htmlErrorBody = "<!DOCTYPE html><html>503 maintenance</html>".toByteArray()
        val mockCall: Call = mockk()
        val mockResponse: Response = mockk()
        val mockBody: ResponseBody = mockk()
        every { mockOkHttpClient.newCall(any<Request>()) } returns mockCall
        every { mockCall.execute() } returns mockResponse
        every { mockResponse.isSuccessful } returns true
        every { mockResponse.code } returns 200
        every { mockResponse.body } returns mockBody
        every { mockBody.contentLength() } returns htmlErrorBody.size.toLong()
        every { mockBody.byteStream() } returns htmlErrorBody.inputStream()
        every { mockBody.close() } just Runs
        every { mockResponse.close() } just Runs

        val result = worker.doWork()

        assertEquals(Result.retry(), result)
        assertFalse(
            "tmp file should be deleted on magic-byte mismatch",
            File(filesDir, "$defaultFilename.tmp").exists(),
        )
        assertFalse(
            "model file should NOT exist when the download has wrong magic",
            File(filesDir, defaultFilename).exists(),
        )
    }

    // --- pre-existing corrupt file -> re-downloads over it ---

    @Test
    fun `doWork deletes pre-existing corrupt model file and re-downloads`() = runTest {
        // The old worker short-circuited on `modelFile.exists()` and never
        // re-downloaded. A truncated or HTML body persisted under the final
        // filename meant AI mode was permanently broken until reinstall.
        val corruptExisting = "<!DOCTYPE html>oops".toByteArray()
        File(filesDir, defaultFilename).writeBytes(corruptExisting)

        val mockCall: Call = mockk()
        val mockResponse: Response = mockk()
        val mockBody: ResponseBody = mockk()
        every { mockOkHttpClient.newCall(any<Request>()) } returns mockCall
        every { mockCall.execute() } returns mockResponse
        every { mockResponse.isSuccessful } returns true
        every { mockResponse.code } returns 200
        every { mockResponse.body } returns mockBody
        every { mockBody.contentLength() } returns validModelBytes.size.toLong()
        every { mockBody.byteStream() } returns validModelBytes.inputStream()
        every { mockBody.close() } just Runs
        every { mockResponse.close() } just Runs

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        val finalFile = File(filesDir, defaultFilename)
        assertTrue(finalFile.exists())
        assertTrue(
            "existing file should have been replaced with fresh download",
            finalFile.readBytes().contentEquals(validModelBytes),
        )
    }

    // --- attempt cap ---

    @Test
    fun `doWork returns failure when runAttemptCount has reached the cap`() = runTest {
        every { mockWorkerParams.runAttemptCount } returns ModelDownloadWorker.MAX_ATTEMPTS
        val cappedWorker = ModelDownloadWorker(
            mockContext,
            mockWorkerParams,
            mockOkHttpClient,
            mockProgressRepository,
        )

        val result = cappedWorker.doWork()

        assertEquals(Result.failure(), result)
        verify { mockOkHttpClient wasNot Called }
    }

    // --- HTTP 5xx error -> retry ---

    @Test
    fun `doWork returns retry on HTTP 503 server error`() = runTest {
        val mockCall: Call = mockk()
        val mockResponse: Response = mockk()
        every { mockOkHttpClient.newCall(any<Request>()) } returns mockCall
        every { mockCall.execute() } returns mockResponse
        every { mockResponse.isSuccessful } returns false
        every { mockResponse.code } returns 503
        every { mockResponse.close() } just Runs

        val result = worker.doWork()

        assertEquals(Result.retry(), result)
    }

    // --- HTTP 4xx error -> failure ---

    @Test
    fun `doWork returns failure on HTTP 404 client error`() = runTest {
        val mockCall: Call = mockk()
        val mockResponse: Response = mockk()
        every { mockOkHttpClient.newCall(any<Request>()) } returns mockCall
        every { mockCall.execute() } returns mockResponse
        every { mockResponse.isSuccessful } returns false
        every { mockResponse.code } returns 404
        every { mockResponse.close() } just Runs

        val result = worker.doWork()

        assertEquals(Result.failure(), result)
    }

    // --- null body -> failure ---

    @Test
    fun `doWork returns failure on null response body`() = runTest {
        val mockCall: Call = mockk()
        val mockResponse: Response = mockk()
        every { mockOkHttpClient.newCall(any<Request>()) } returns mockCall
        every { mockCall.execute() } returns mockResponse
        every { mockResponse.isSuccessful } returns true
        every { mockResponse.body } returns null
        every { mockResponse.code } returns 200
        every { mockResponse.close() } just Runs

        val result = worker.doWork()

        assertEquals(Result.failure(), result)
    }

    // --- network exception -> retry, tmp preserved for resume ---

    @Test
    fun `doWork preserves tmp and returns retry on network exception`() = runTest {
        // New resume-from-partial semantics: the tmp file is the raw bytes we
        // already spent bandwidth on, so we keep it across retries. The next
        // attempt will send `Range: bytes=<length>-` and continue. The
        // previous "delete on any failure" behaviour was the root cause of
        // the user-visible "went back to 0%" bug.
        val tmpFile = File(filesDir, "$defaultFilename.tmp")
        // Simulate some prior progress so we can assert it survives.
        tmpFile.writeBytes(litertlmMagic + ByteArray(100))
        val priorLen = tmpFile.length()
        every { mockOkHttpClient.newCall(any<Request>()) } throws IOException("connection reset")

        val result = worker.doWork()

        assertEquals(Result.retry(), result)
        assertTrue("tmp file should survive a network exception", tmpFile.exists())
        assertEquals("tmp file size should be unchanged", priorLen, tmpFile.length())
    }

    // --- cancellation -> rethrow, tmp preserved for resume ---

    @Test
    fun `doWork preserves tmp and rethrows on cancellation`() = runTest {
        val tmpFile = File(filesDir, "$defaultFilename.tmp")
        tmpFile.writeBytes(litertlmMagic + ByteArray(50))
        val priorLen = tmpFile.length()
        every { mockOkHttpClient.newCall(any<Request>()) } throws CancellationException("cancelled")

        var threw = false
        try {
            worker.doWork()
        } catch (e: CancellationException) {
            threw = true
        }
        assertTrue("CancellationException should be rethrown", threw)
        assertTrue("tmp file should survive cancellation", tmpFile.exists())
        assertEquals("tmp file size should be unchanged", priorLen, tmpFile.length())
    }

    // --- constants ---

    @Test
    fun `WORK_NAME constant is defined`() {
        assertEquals("model_download", ModelDownloadWorker.WORK_NAME)
    }

    @Test
    fun `MODEL_FILENAME constant is defined`() {
        assertEquals("gemma3-1b-it-int4.task", ModelDownloadWorker.MODEL_FILENAME)
    }

    @Test
    fun `KEY_MODEL_ID constant is defined`() {
        assertEquals("modelId", ModelDownloadWorker.KEY_MODEL_ID)
    }

    // --- placeholder URL guard ---

    @Test
    fun `doWork returns failure and skips network when catalog entry has placeholder URL`() = runTest {
        // Gemma 3 descriptor in the catalog ships with a placeholder URL
        // (HF-gated), so selecting it triggers the short-circuit path before
        // any HTTP call lands.
        every { mockWorkerParams.inputData } returns inputDataFor(ModelCatalog.gemma3_1B_Task.id)
        val placeholderWorker = ModelDownloadWorker(
            mockContext,
            mockWorkerParams,
            mockOkHttpClient,
            mockProgressRepository,
        )

        val result = placeholderWorker.doWork()

        assertEquals(Result.failure(), result)
        verify { mockOkHttpClient wasNot Called }
    }

    // --- unknown model id -> failure ---

    @Test
    fun `doWork returns failure when modelId is not in the catalog`() = runTest {
        every { mockWorkerParams.inputData } returns inputDataFor("nonexistent-model-id")
        val strayWorker = ModelDownloadWorker(
            mockContext,
            mockWorkerParams,
            mockOkHttpClient,
            mockProgressRepository,
        )

        val result = strayWorker.doWork()

        assertEquals(Result.failure(), result)
        verify { mockOkHttpClient wasNot Called }
    }

    // --- model id drives URL + filename ---

    @Test
    fun `doWork given a modelId uses that descriptor's URL and filename`() = runTest {
        // Contract: swapping the modelId in inputData swaps the download
        // target (URL written into the Request) and the on-disk filename
        // without the caller needing to tell the worker directly.
        every { mockWorkerParams.inputData } returns inputDataFor(defaultModel.id)

        val capturedRequest = slot<Request>()
        val mockCall: Call = mockk()
        val mockResponse: Response = mockk()
        val mockBody: ResponseBody = mockk()
        every { mockOkHttpClient.newCall(capture(capturedRequest)) } returns mockCall
        every { mockCall.execute() } returns mockResponse
        every { mockResponse.isSuccessful } returns true
        every { mockResponse.code } returns 200
        every { mockResponse.body } returns mockBody
        every { mockBody.contentLength() } returns validModelBytes.size.toLong()
        every { mockBody.byteStream() } returns validModelBytes.inputStream()
        every { mockBody.close() } just Runs
        every { mockResponse.close() } just Runs

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        assertEquals(
            "URL must come from the descriptor, not a constant",
            defaultModel.url,
            capturedRequest.captured.url.toString(),
        )
        assertTrue(
            "final file must use the descriptor's fileName",
            File(filesDir, defaultModel.fileName).exists(),
        )
    }

    // --- resume-from-partial semantics ---

    @Test
    fun `doWork sends Range header and appends when tmp file already has bytes`() = runTest {
        // Pre-seed tmp with 100 bytes of "previous attempt" payload. The new
        // worker should see the tmp, send `Range: bytes=100-`, append the
        // remaining bytes returned as 206 Partial Content, then finalise.
        val tmpFile = File(filesDir, "$defaultFilename.tmp")
        val firstHalf = litertlmMagic + ByteArray(92) { it.toByte() } // 100 bytes
        tmpFile.writeBytes(firstHalf)

        val remainder = ByteArray(50) { (it + 100).toByte() } // bytes 100..149
        val totalExpected = 150L
        val mockCall: Call = mockk()
        val mockResponse: Response = mockk()
        val mockBody: ResponseBody = mockk()
        val capturedRequest = slot<Request>()
        every { mockOkHttpClient.newCall(capture(capturedRequest)) } returns mockCall
        every { mockCall.execute() } returns mockResponse
        every { mockResponse.code } returns 206
        every { mockResponse.isSuccessful } returns true
        every { mockResponse.body } returns mockBody
        // Partial-Content body length = only the remainder, not the whole file.
        every { mockBody.contentLength() } returns remainder.size.toLong()
        every { mockBody.byteStream() } returns remainder.inputStream()
        every { mockBody.close() } just Runs
        every { mockResponse.close() } just Runs

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        assertEquals(
            "Range header should resume from the tmp file's current size",
            "bytes=100-",
            capturedRequest.captured.header("Range"),
        )
        val finalFile = File(filesDir, defaultFilename)
        assertTrue("final file should exist", finalFile.exists())
        assertEquals(
            "final file should be the pre-existing partial + remainder",
            totalExpected,
            finalFile.length(),
        )
        // Content = firstHalf + remainder, verifying we appended rather than
        // truncating the resumed tmp.
        assertTrue(
            "final bytes must be the concatenation of partial + remainder",
            finalFile.readBytes().contentEquals(firstHalf + remainder),
        )
    }

    @Test
    fun `doWork wipes tmp and returns retry on HTTP 416 Range Not Satisfiable`() = runTest {
        // Partial tmp is larger than the server's current total, or tmp is
        // corrupt somehow — 416 means "your Range is outside the resource".
        // The safe recovery is to drop the tmp and retry from zero.
        val tmpFile = File(filesDir, "$defaultFilename.tmp")
        tmpFile.writeBytes(ByteArray(200))

        val mockCall: Call = mockk()
        val mockResponse: Response = mockk()
        every { mockOkHttpClient.newCall(any<Request>()) } returns mockCall
        every { mockCall.execute() } returns mockResponse
        every { mockResponse.isSuccessful } returns false
        every { mockResponse.code } returns 416
        every { mockResponse.close() } just Runs

        val result = worker.doWork()

        assertEquals(Result.retry(), result)
        assertFalse("tmp must be wiped on HTTP 416", tmpFile.exists())
    }

    @Test
    fun `doWork restarts from byte 0 when server ignores Range header and returns 200`() = runTest {
        // Some CDNs silently ignore Range and return the whole file as 200.
        // If we naively appended that to the existing tmp we'd double the
        // file size and the integrity check would fire. Worker must detect
        // the 200 and truncate the tmp before writing.
        val tmpFile = File(filesDir, "$defaultFilename.tmp")
        tmpFile.writeBytes(ByteArray(100) { 0xAA.toByte() })

        val mockCall: Call = mockk()
        val mockResponse: Response = mockk()
        val mockBody: ResponseBody = mockk()
        every { mockOkHttpClient.newCall(any<Request>()) } returns mockCall
        every { mockCall.execute() } returns mockResponse
        every { mockResponse.isSuccessful } returns true
        every { mockResponse.code } returns 200
        every { mockResponse.body } returns mockBody
        every { mockBody.contentLength() } returns validModelBytes.size.toLong()
        every { mockBody.byteStream() } returns validModelBytes.inputStream()
        every { mockBody.close() } just Runs
        every { mockResponse.close() } just Runs

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        val finalFile = File(filesDir, defaultFilename)
        assertTrue(finalFile.readBytes().contentEquals(validModelBytes))
    }
}
