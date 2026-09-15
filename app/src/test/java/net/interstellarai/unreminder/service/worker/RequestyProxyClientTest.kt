package net.interstellarai.unreminder.service.worker

import kotlinx.coroutines.test.runTest
import net.interstellarai.unreminder.data.db.VariationEntity
import net.interstellarai.unreminder.domain.model.ActivityMode
import net.interstellarai.unreminder.domain.model.VariantShape
import net.interstellarai.unreminder.service.notification.MascotSprite
import net.interstellarai.unreminder.service.notification.MascotSprites
import org.json.JSONException
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
    fun `generateBatch returns list of GeneratedVariants on 200`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"variants":[{"text":"v1","shape":"QUESTION"},{"text":"v2","shape":"TIMEBOXED","actionUrl":"https://youtube.com/results?search_query=test"},{"text":"v3","shape":"TERSE"}]}""")
                .addHeader("Content-Type", "application/json")
        )

        val batch = proxyClient.generateBatch(
            habitTitle = "Meditate",
            habitTags = emptyList(),
            locationName = "",
            timeOfDay = "",
            personalContext = "",
            sprites = emptyList(),
            supportedModes = emptySet(),
            n = 3,
            workerUrl = baseUrl(),
            workerToken = "secret",
        )
        val result = batch.variants
        assertEquals(3, result.size)
        assertEquals("v1", result[0].text)
        assertEquals(VariantShape.QUESTION, result[0].shape)
        assertNull(result[0].actionUrl)
        assertEquals("v2", result[1].text)
        assertEquals(VariantShape.TIMEBOXED, result[1].shape)
        assertEquals("https://youtube.com/results?search_query=test", result[1].actionUrl)
        assertEquals("v3", result[2].text)
        assertEquals(VariantShape.TERSE, result[2].shape)
        assertNull(result[2].actionUrl)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/v1/generate/batch", recorded.path)
        assertEquals("Bearer secret", recorded.getHeader("Authorization"))
    }

    @Test
    fun `generateBatch parses spriteTag and tolerates its absence`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"variants":[{"text":"v1","shape":"STATEMENT","spriteTag":"astronaut_zero_g"},{"text":"v2","shape":"STATEMENT"},{"text":"v3","shape":"STATEMENT","spriteTag":""}]}""")
                .addHeader("Content-Type", "application/json")
        )

        val result = proxyClient.generateBatch(
            habitTitle = "Meditate",
            habitTags = emptyList(),
            locationName = "",
            timeOfDay = "",
            personalContext = "",
            sprites = MascotSprites.entries,
            supportedModes = emptySet(),
            n = 3,
            workerUrl = baseUrl(),
            workerToken = "secret",
        )

        assertEquals("astronaut_zero_g", result.variants[0].spriteTag)
        assertNull(result.variants[1].spriteTag)
        assertNull(result.variants[2].spriteTag)
    }

    @Test
    fun `generateBatch sends the sprite vocabulary in the request body`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"variants":[{"text":"v1","shape":"STATEMENT"}]}""")
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
            supportedModes = emptySet(),
            n = 1,
            workerUrl = baseUrl(),
            workerToken = "secret",
        )

        val body = JSONObject(server.takeRequest().body.readUtf8())
        val sent = body.getJSONArray("sprites")
        assertEquals(2, sent.length())
        assertEquals("astronaut_zero_g", sent.getJSONObject(0).getString("tag"))
        assertEquals("a cat in an orange spacesuit", sent.getJSONObject(0).getString("description"))
        assertEquals("chef_pan_flip", sent.getJSONObject(1).getString("tag"))
    }

    @Test
    fun `generateBatch parses modes and reads a missing array or unknown mode as neutral`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"variants":[""" +
                    """{"text":"v1","shape":"STATEMENT","modes":["WALKING","TRANSPORT"]},""" +
                    """{"text":"v2","shape":"STATEMENT","modes":[]},""" +
                    """{"text":"v3","shape":"STATEMENT"},""" +
                    """{"text":"v4","shape":"STATEMENT","modes":["CYCLING"]}]}"""
                )
                .addHeader("Content-Type", "application/json")
        )

        val result = proxyClient.generateBatch("Meditate", emptyList(), "", "", "", emptyList(), emptySet(), 4, baseUrl(), "secret").variants

        assertEquals(setOf(ActivityMode.WALKING, ActivityMode.TRANSPORT), result[0].modes)
        assertEquals(emptySet<ActivityMode>(), result[1].modes)
        assertEquals(emptySet<ActivityMode>(), result[2].modes)
        assertEquals(emptySet<ActivityMode>(), result[3].modes)
    }

    @Test
    fun `generateBatch sends the habit's supported modes by name in the request body`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"variants":[{"text":"v1","shape":"STATEMENT"}]}""")
                .addHeader("Content-Type", "application/json")
        )

        proxyClient.generateBatch(
            "Meditate", emptyList(), "", "", "", emptyList(),
            setOf(ActivityMode.SITTING, ActivityMode.TRANSPORT), 1, baseUrl(), "secret",
        )

        val sent = JSONObject(server.takeRequest().body.readUtf8()).getJSONArray("supportedModes")
        assertEquals(setOf("SITTING", "TRANSPORT"), (0 until sent.length()).map { sent.getString(it) }.toSet())
    }

    @Test
    fun `generateBatch reads generationVersion and reports UNVERSIONED when absent`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"variants":[{"text":"v1","shape":"STATEMENT"}],"generationVersion":3}""")
                .addHeader("Content-Type", "application/json")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"variants":[{"text":"v1","shape":"STATEMENT"}]}""")
                .addHeader("Content-Type", "application/json")
        )

        val versioned = proxyClient.generateBatch("Meditate", emptyList(), "", "", "", emptyList(), emptySet(), 1, baseUrl(), "secret")
        val legacy = proxyClient.generateBatch("Meditate", emptyList(), "", "", "", emptyList(), emptySet(), 1, baseUrl(), "secret")

        assertEquals(3, versioned.generationVersion)
        assertEquals(VariationEntity.UNVERSIONED, legacy.generationVersion)
    }

    @Test
    fun `generateBatch throws JSONException when a variant has no shape`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"variants":[{"text":"v1","shape":"STATEMENT"},{"text":"v2"}]}""")
                .addHeader("Content-Type", "application/json")
        )

        assertFailsWith<JSONException> {
            proxyClient.generateBatch("Meditate", emptyList(), "", "", "", emptyList(), emptySet(), 2, baseUrl(), "secret")
        }
    }

    @Test
    fun `generateBatch throws JSONException when a shape is outside the six shapes`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"variants":[{"text":"v1","shape":"plea"}]}""")
                .addHeader("Content-Type", "application/json")
        )

        assertFailsWith<JSONException> {
            proxyClient.generateBatch("Meditate", emptyList(), "", "", "", emptyList(), emptySet(), 1, baseUrl(), "secret")
        }
    }

    @Test
    fun `generateBatch throws WorkerAuthException on 401`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

        assertFailsWith<WorkerAuthException> {
            proxyClient.generateBatch("Meditate", emptyList(), "", "", "", emptyList(), emptySet(), 1, baseUrl(), "bad")
        }
    }

    @Test
    fun `generateBatch throws SpendCapExceededException on 402`() = runTest {
        server.enqueue(MockResponse().setResponseCode(402).setBody("""{"error":"cap"}"""))

        assertFailsWith<SpendCapExceededException> {
            proxyClient.generateBatch("Meditate", emptyList(), "", "", "", emptyList(), emptySet(), 1, baseUrl(), "secret")
        }
    }

    @Test
    fun `generateBatch throws WorkerError on 500`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val ex = assertFailsWith<WorkerError> {
            proxyClient.generateBatch("Meditate", emptyList(), "", "", "", emptyList(), emptySet(), 1, baseUrl(), "secret")
        }
        assertEquals(500, ex.code)
    }

    @Test
    fun `generateBatch throws Exception on empty body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        assertFailsWith<Exception> {
            proxyClient.generateBatch("Meditate", emptyList(), "", "", "", emptyList(), emptySet(), 1, baseUrl(), "secret")
        }
    }

    // --- generationVersion ---

    @Test
    fun `generationVersion reads the field from a GET on the public health route`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status":"ok","spendUsedToday":0.01,"generationVersion":4}""")
                .addHeader("Content-Type", "application/json")
        )

        assertEquals(4, proxyClient.generationVersion(baseUrl()))

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/v1/health", recorded.path)
        assertNull(recorded.getHeader("Authorization"))
    }

    @Test
    fun `generationVersion reports UNVERSIONED when the worker omits the field`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status":"ok","spendUsedToday":0.01}""")
                .addHeader("Content-Type", "application/json")
        )

        assertEquals(VariationEntity.UNVERSIONED, proxyClient.generationVersion(baseUrl()))
    }

    @Test
    fun `generationVersion throws WorkerError on 503`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":"Service misconfigured"}"""))

        val ex = assertFailsWith<WorkerError> { proxyClient.generationVersion(baseUrl()) }
        assertEquals(503, ex.code)
    }
}
