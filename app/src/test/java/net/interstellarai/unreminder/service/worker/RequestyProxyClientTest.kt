package net.interstellarai.unreminder.service.worker

import kotlinx.coroutines.test.runTest
import net.interstellarai.unreminder.data.db.HabitEntity
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

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
                .setBody("""{"fullDescription":"Meditate daily","lowFloorDescription":"Just sit"}""")
                .addHeader("Content-Type", "application/json")
        )

        val result = proxyClient.habitFields("Meditate", baseUrl(), "secret")
        assertEquals("Meditate daily", result.fullDescription)
        assertEquals("Just sit", result.lowFloorDescription)
    }

    @Test
    fun `habitFields throws SpendCapExceededException on 402`() = runTest {
        server.enqueue(MockResponse().setResponseCode(402).setBody("""{"error":"cap"}"""))

        assertThrows(SpendCapExceededException::class.java) {
            kotlinx.coroutines.test.runTest {
                proxyClient.habitFields("Meditate", baseUrl(), "secret")
            }
        }
    }

    @Test
    fun `habitFields throws RuntimeException on 500`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        assertThrows(RuntimeException::class.java) {
            kotlinx.coroutines.test.runTest {
                proxyClient.habitFields("Meditate", baseUrl(), "secret")
            }
        }
    }

    @Test
    fun `preview returns String on 200`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"text":"Time to meditate!"}""")
                .addHeader("Content-Type", "application/json")
        )

        val habit = HabitEntity(name = "Meditate", fullDescription = "Daily practice", lowFloorDescription = "Sit")
        val result = proxyClient.preview(habit, "Home", baseUrl(), "secret")
        assertEquals("Time to meditate!", result)
    }

    @Test
    fun `preview throws SpendCapExceededException on 402`() = runTest {
        server.enqueue(MockResponse().setResponseCode(402).setBody("""{"error":"cap"}"""))

        val habit = HabitEntity(name = "Meditate", fullDescription = "Daily practice", lowFloorDescription = "Sit")
        assertThrows(SpendCapExceededException::class.java) {
            kotlinx.coroutines.test.runTest {
                proxyClient.preview(habit, "Home", baseUrl(), "secret")
            }
        }
    }
}
