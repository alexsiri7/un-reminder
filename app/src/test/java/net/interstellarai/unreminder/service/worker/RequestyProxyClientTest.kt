package net.interstellarai.unreminder.service.worker

import kotlinx.coroutines.test.runTest
import net.interstellarai.unreminder.service.notification.MascotSprite
import net.interstellarai.unreminder.service.notification.MascotSprites
import org.json.JSONObject
import kotlin.test.assertFailsWith
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `habitFields throws WorkerError when descriptionLadder missing from 200 response`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"fullDescription":"foo","lowFloorDescription":"bar"}""")
                .addHeader("Content-Type", "application/json")
        )

        val ex = assertFailsWith<WorkerError> {
            proxyClient.habitFields("Meditate", baseUrl(), "secret")
        }
        assertEquals(200, ex.code)
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

    // --- generateBatch ---

    @Test
    fun `generateBatch returns list of NotificationVariants on 200`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"variants":[{"text":"v1"},{"text":"v2","actionUrl":"https://youtube.com/results?search_query=test"},{"text":"v3"}]}""")
                .addHeader("Content-Type", "application/json")
        )

        val result = proxyClient.generateBatch(
            habitTitle = "Meditate",
            habitTags = emptyList(),
            locationName = "",
            timeOfDay = "",
            personalContext = "",
            sprites = emptyList(),
            n = 3,
            workerUrl = baseUrl(),
            workerSecret = "secret",
        )
        assertEquals(3, result.size)
        assertEquals("v1", result[0].text)
        assertNull(result[0].actionUrl)
        assertEquals("v2", result[1].text)
        assertEquals("https://youtube.com/results?search_query=test", result[1].actionUrl)
        assertEquals("v3", result[2].text)
        assertNull(result[2].actionUrl)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/v1/generate/batch", recorded.path)
        assertEquals("secret", recorded.getHeader("X-UR-Secret"))
    }

    @Test
    fun `generateBatch parses spriteTag and tolerates its absence`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"variants":[{"text":"v1","spriteTag":"astronaut_zero_g"},{"text":"v2"},{"text":"v3","spriteTag":""}]}""")
                .addHeader("Content-Type", "application/json")
        )

        val result = proxyClient.generateBatch(
            habitTitle = "Meditate",
            habitTags = emptyList(),
            locationName = "",
            timeOfDay = "",
            personalContext = "",
            sprites = MascotSprites.entries,
            n = 3,
            workerUrl = baseUrl(),
            workerSecret = "secret",
        )

        assertEquals("astronaut_zero_g", result[0].spriteTag)
        assertNull(result[1].spriteTag)
        assertNull(result[2].spriteTag)
    }

    @Test
    fun `generateBatch sends the sprite vocabulary in the request body`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"variants":[{"text":"v1"}]}""")
                .addHeader("Content-Type", "application/json")
        )

        val sprites = listOf(
            MascotSprite("astronaut_zero_g", "a cat in an orange spacesuit", 1),
            MascotSprite("chef_pan_flip", "a cat flipping an egg", 2),
        )
        proxyClient.generateBatch(
            habitTitle = "Meditate",
            habitTags = emptyList(),
            locationName = "",
            timeOfDay = "",
            personalContext = "",
            sprites = sprites,
            n = 1,
            workerUrl = baseUrl(),
            workerSecret = "secret",
        )

        val body = JSONObject(server.takeRequest().body.readUtf8())
        val sent = body.getJSONArray("sprites")
        assertEquals(2, sent.length())
        assertEquals("astronaut_zero_g", sent.getJSONObject(0).getString("tag"))
        assertEquals("a cat in an orange spacesuit", sent.getJSONObject(0).getString("description"))
        assertEquals("chef_pan_flip", sent.getJSONObject(1).getString("tag"))
    }

    @Test
    fun `generateBatch throws WorkerAuthException on 401`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

        assertFailsWith<WorkerAuthException> {
            proxyClient.generateBatch("Meditate", emptyList(), "", "", "", emptyList(), 1, baseUrl(), "bad")
        }
    }

    @Test
    fun `generateBatch throws SpendCapExceededException on 402`() = runTest {
        server.enqueue(MockResponse().setResponseCode(402).setBody("""{"error":"cap"}"""))

        assertFailsWith<SpendCapExceededException> {
            proxyClient.generateBatch("Meditate", emptyList(), "", "", "", emptyList(), 1, baseUrl(), "secret")
        }
    }

    @Test
    fun `generateBatch throws WorkerError on 500`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val ex = assertFailsWith<WorkerError> {
            proxyClient.generateBatch("Meditate", emptyList(), "", "", "", emptyList(), 1, baseUrl(), "secret")
        }
        assertEquals(500, ex.code)
    }

    @Test
    fun `generateBatch throws Exception on empty body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        assertFailsWith<Exception> {
            proxyClient.generateBatch("Meditate", emptyList(), "", "", "", emptyList(), 1, baseUrl(), "secret")
        }
    }
}
