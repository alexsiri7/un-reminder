package net.interstellarai.unreminder.service.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkerTokenTest {

    private val id = "0123456789abcdef"
    private val secret = "f".repeat(64)
    private val token = "ur1_${id}_$secret"

    @Test
    fun `accepts a well-formed token`() {
        assertTrue(WorkerToken.isWellFormed(token))
    }

    @Test
    fun `rejects anything that is not exactly the token shape`() {
        assertFalse("uppercase hex", WorkerToken.isWellFormed("ur1_${id.uppercase()}_$secret"))
        assertFalse("wrong prefix", WorkerToken.isWellFormed("ur2_${id}_$secret"))
        assertFalse("short id", WorkerToken.isWellFormed("ur1_${id.drop(1)}_$secret"))
        assertFalse("short secret", WorkerToken.isWellFormed("ur1_${id}_${secret.drop(1)}"))
        assertFalse("long secret", WorkerToken.isWellFormed("ur1_${id}_${secret}f"))
        assertFalse("surrounding whitespace", WorkerToken.isWellFormed(" $token "))
        assertFalse("empty", WorkerToken.isWellFormed(""))
    }

    @Test
    fun `displayId is the non-secret ur1_id prefix`() {
        assertEquals("ur1_$id", WorkerToken.displayId(token))
    }
}
