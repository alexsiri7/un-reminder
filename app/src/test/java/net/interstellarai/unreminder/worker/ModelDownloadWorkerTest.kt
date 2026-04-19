package net.interstellarai.unreminder.worker

import android.content.Context
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import io.mockk.Called
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import net.interstellarai.unreminder.service.llm.ModelConfig
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
    private val testModelUrl = "https://example.test/model.task"

    private lateinit var filesDir: File
    private lateinit var worker: ModelDownloadWorker

    @Before
    fun setup() {
        filesDir = createTempDir()
        every { mockContext.filesDir } returns filesDir
        worker = ModelDownloadWorker(mockContext, mockWorkerParams, mockOkHttpClient, testModelUrl)
    }

    @After
    fun teardown() {
        filesDir.deleteRecursively()
    }

    // --- idempotency ---

    @Test
    fun `doWork returns success immediately when model file already exists`() = runTest {
        File(filesDir, ModelDownloadWorker.MODEL_FILENAME).createNewFile()

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
        val content = "fake-model-bytes".toByteArray()
        every { mockOkHttpClient.newCall(any<Request>()) } returns mockCall
        every { mockCall.execute() } returns mockResponse
        every { mockResponse.isSuccessful } returns true
        every { mockResponse.body } returns mockBody
        every { mockBody.contentLength() } returns content.size.toLong()
        every { mockBody.byteStream() } returns content.inputStream()
        every { mockBody.close() } just Runs
        every { mockResponse.close() } just Runs

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        assertTrue(File(filesDir, ModelDownloadWorker.MODEL_FILENAME).exists())
        assertFalse(File(filesDir, "${ModelDownloadWorker.MODEL_FILENAME}.tmp").exists())
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

    // --- network exception -> tmp cleanup + retry ---

    @Test
    fun `doWork cleans up tmp file and returns retry on network exception`() = runTest {
        val tmpFile = File(filesDir, "${ModelDownloadWorker.MODEL_FILENAME}.tmp")
        every { mockOkHttpClient.newCall(any<Request>()) } throws IOException("connection reset")

        val result = worker.doWork()

        assertEquals(Result.retry(), result)
        assertFalse("tmp file should be deleted on exception", tmpFile.exists())
    }

    // --- cancellation -> tmp cleanup ---

    @Test
    fun `doWork cleans up tmp file and rethrows on cancellation`() = runTest {
        val tmpFile = File(filesDir, "${ModelDownloadWorker.MODEL_FILENAME}.tmp")
        every { mockOkHttpClient.newCall(any<Request>()) } throws CancellationException("cancelled")

        var threw = false
        try {
            worker.doWork()
        } catch (e: CancellationException) {
            threw = true
        }
        assertTrue("CancellationException should be rethrown", threw)
        assertFalse("tmp file should be deleted on cancellation", tmpFile.exists())
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

    // --- placeholder URL guard ---

    @Test
    fun `doWork returns failure and skips network when URL is the placeholder`() = runTest {
        val placeholderWorker = ModelDownloadWorker(
            mockContext,
            mockWorkerParams,
            mockOkHttpClient,
            ModelConfig.PLACEHOLDER_URL
        )

        val result = placeholderWorker.doWork()

        assertEquals(Result.failure(), result)
        verify { mockOkHttpClient wasNot Called }
    }

    @Test
    fun `doWork returns failure and skips network when URL is blank`() = runTest {
        val blankWorker = ModelDownloadWorker(
            mockContext,
            mockWorkerParams,
            mockOkHttpClient,
            ""
        )

        val result = blankWorker.doWork()

        assertEquals(Result.failure(), result)
        verify { mockOkHttpClient wasNot Called }
    }
}
