package net.interstellarai.unreminder.service.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkerTokenTest {

    private val id = "0123456789abcdef"
    private val token = "ur1_${id}_${"f".repeat(64)}"

    /**
     * worker/test/fixtures/token-format.txt, on the test classpath via build.gradle.kts and
     * asserted against by the Worker's tokens.test.ts too; each line is `ok` or `bad <reason>`,
     * a tab, then the token.
     */
    private fun fixtureCases(): List<Pair<String, String>> {
        val fixture = checkNotNull(javaClass.getResourceAsStream("/token-format.txt")) {
            "token-format.txt is not on the test classpath"
        }
        return fixture.bufferedReader().readLines()
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line -> line.split("\t", limit = 2).let { it[0] to it[1] } }
    }

    @Test
    fun `classifies every token in the shared fixture the way the Worker does`() {
        val cases = fixtureCases()
        assertTrue("fixture has no accepted token", cases.any { (verdict, _) -> verdict == "ok" })
        assertTrue("fixture has no rejected token", cases.any { (verdict, _) -> verdict != "ok" })

        for ((verdict, candidate) in cases) {
            assertEquals(verdict, verdict == "ok", WorkerToken.isWellFormed(candidate))
        }
    }

    @Test
    fun `displayId is the non-secret ur1_id prefix`() {
        assertEquals("ur1_$id", WorkerToken.displayId(token))
    }
}
