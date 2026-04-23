package net.interstellarai.unreminder.service.worker

import kotlinx.coroutines.test.runTest
import kotlin.test.assertFailsWith
import net.interstellarai.unreminder.data.db.HabitEntity
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RequestyProxyClientTest {

    private val server = MockWebServer()
    private val client = OkHttpClient()
    private lateinit var proxyClient: RequestyProxyClient

    @Before
    fun setUp() {
        server.start()
        proxyClient = RequestyProxyClient(client)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun baseUrl(): String = server.url("/").toString().trimEnd('/')

    @Test
    fun `habitFields returns AiHabitFields on 200`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"descriptionLadder":["Just sit","","","Meditate daily","",""]}""")
                .addHeader("Content-Type", "application/json")
        )

        val result = proxyClient.habitFields("Meditate", baseUrl(), "secret")
        assertEquals(6, result.descriptionLadder.size)
        assertEquals("Just sit", result.descriptionLadder[0])
        assertEquals("Meditate daily", result.descriptionLadder[3])
    }

    @Test
    fun `habitFields throws WorkerAuthException on 401`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

        assertFailsWith<WorkerAuthException> {
            proxyClient.habitFields("Meditate", baseUrl(), "wrong-secret")
        }
    }

    @Test
    fun `habitFields throws SpendCapExceededException on 402`() = runTest {
        server.enqueue(MockResponse().setResponseCode(402).setBody("""{"error":"cap"}"""))

        assertFailsWith<SpendCapExceededException> {
            proxyClient.habitFields("Meditate", baseUrl(), "secret")
        }
    }

    @Test
    fun `habitFields throws WorkerError on 500`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val ex = assertFailsWith<WorkerError> {
            proxyClient.habitFields("Meditate", baseUrl(), "secret")
        }
        assertEquals(500, ex.code)
    }

    @Test
    fun `preview returns String on 200`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"text":"Time to meditate!"}""")
                .addHeader("Content-Type", "application/json")
        )

        val habit = HabitEntity(name = "Meditate")
        val result = proxyClient.preview(habit, "Home", baseUrl(), "secret")
        assertEquals("Time to meditate!", result)
    }

    @Test
    fun `preview throws WorkerAuthException on 401`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

        val habit = HabitEntity(name = "Meditate")
        assertFailsWith<WorkerAuthException> {
            proxyClient.preview(habit, "Daily practice", "Home", baseUrl(), "wrong-secret")
        }
    }

    @Test
    fun `preview throws SpendCapExceededException on 402`() = runTest {
        server.enqueue(MockResponse().setResponseCode(402).setBody("""{"error":"cap"}"""))

        val habit = HabitEntity(name = "Meditate")
        assertFailsWith<SpendCapExceededException> {
            proxyClient.preview(habit, "Daily practice", "Home", baseUrl(), "secret")
        }
    }

    @Test
    fun `preview throws WorkerError on 500`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val habit = HabitEntity(name = "Meditate")
        val ex = assertFailsWith<WorkerError> {
            proxyClient.preview(habit, "Daily practice", "Home", baseUrl(), "secret")
        }
        assertEquals(500, ex.code)
    }

    // --- generateBatch ---

    @Test
    fun `generateBatch returns list of strings on 200`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"variants":["v1","v2","v3"]}""")
                .addHeader("Content-Type", "application/json")
        )

        val result = proxyClient.generateBatch(
            habitTitle = "Meditate",
            habitTags = emptyList(),
            locationName = "",
            timeOfDay = "",
            n = 3,
            workerUrl = baseUrl(),
            workerSecret = "secret",
        )
        assertEquals(listOf("v1", "v2", "v3"), result)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/v1/generate/batch", recorded.path)
        assertEquals("secret", recorded.getHeader("X-UR-Secret"))
    }

    @Test
    fun `generateBatch throws WorkerAuthException on 401`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

        assertFailsWith<WorkerAuthException> {
            proxyClient.generateBatch("Meditate", emptyList(), "", "", 1, baseUrl(), "bad")
        }
    }

    @Test
    fun `generateBatch throws SpendCapExceededException on 402`() = runTest {
        server.enqueue(MockResponse().setResponseCode(402).setBody("""{"error":"cap"}"""))

        assertFailsWith<SpendCapExceededException> {
            proxyClient.generateBatch("Meditate", emptyList(), "", "", 1, baseUrl(), "secret")
        }
    }

    @Test
    fun `generateBatch throws WorkerError on 500`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val ex = assertFailsWith<WorkerError> {
            proxyClient.generateBatch("Meditate", emptyList(), "", "", 1, baseUrl(), "secret")
        }
        assertEquals(500, ex.code)
    }

    @Test
    fun `generateBatch throws Exception on empty body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        assertFailsWith<Exception> {
            proxyClient.generateBatch("Meditate", emptyList(), "", "", 1, baseUrl(), "secret")
        }
    }
}
