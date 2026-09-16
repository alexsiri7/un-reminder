package net.interstellarai.unreminder.service.worker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.interstellarai.unreminder.data.db.VariationEntity
import net.interstellarai.unreminder.domain.model.ActivityMode
import net.interstellarai.unreminder.domain.model.AiHabitFields
import net.interstellarai.unreminder.domain.model.GeneratedBatch
import net.interstellarai.unreminder.domain.model.GeneratedVariant
import net.interstellarai.unreminder.domain.model.SpendCapScope
import net.interstellarai.unreminder.domain.model.SpendCapType
import net.interstellarai.unreminder.domain.model.VariantShape
import net.interstellarai.unreminder.service.notification.MascotSprite
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Mirrors `INTEGRITY_HEADER` in worker/src/lib/integrity.ts; worker/test/fixtures/integrity-wire.txt pins both. */
const val INTEGRITY_HEADER = "X-Play-Integrity-Token"

/**
 * The `error` of the Worker's own 403 body (worker/src/middleware/integrity.ts; integrity-wire.txt pins
 * both); any other 403 (a Cloudflare rule, say) stays a plain [WorkerError].
 */
private const val INTEGRITY_REJECTED = "Play Integrity check failed"

private fun Response.throwOnError(integrity: IntegrityTokenResult?): Nothing {
    val text = body?.string() ?: ""
    when (code) {
        401 -> throw WorkerAuthException()
        402 -> {
            val json = runCatching { JSONObject(text) }.getOrNull()
            throw SpendCapExceededException(
                capScope = json?.optString("capScope")?.let(SpendCapScope::fromWire),
                capType = json?.optString("capType")?.let(SpendCapType::fromWire),
            )
        }
        403 -> {
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (json?.optString("error") == INTEGRITY_REJECTED) {
                throw WorkerIntegrityException(
                    reason = json.optString("reason").ifEmpty { "unknown" },
                    retryable = integrity is IntegrityTokenResult.Unavailable && integrity.retryable,
                )
            }
        }
    }
    throw WorkerError(code, text)
}

@Singleton
class RequestyProxyClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val integrityTokenProvider: IntegrityTokenProvider,
) {
    private suspend fun post(path: String, payload: JSONObject, workerUrl: String, token: String): JSONObject {
        val body = payload.toString()
        val integrity = integrityTokenProvider.token(sha256Hex(body))
        val request = Request.Builder()
            .url("${workerUrl.trimEnd('/')}/$path")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
            .apply { if (integrity is IntegrityTokenResult.Token) addHeader(INTEGRITY_HEADER, integrity.value) }
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        return execute(request, integrity)
    }

    private fun get(path: String, workerUrl: String): JSONObject =
        execute(
            Request.Builder()
                .url("${workerUrl.trimEnd('/')}/$path")
                .addHeader("Accept", "application/json")
                .get()
                .build(),
            integrity = null,
        )

    private fun execute(request: Request, integrity: IntegrityTokenResult?): JSONObject =
        okHttpClient.newCall(request).execute().use { response ->
            if (response.code !in 200..299) response.throwOnError(integrity)
            JSONObject(response.body?.string() ?: throw RuntimeException("Worker returned empty body"))
        }

    /** The Worker hashes the exact bytes it receives the same way (worker/src/lib/integrity.ts). */
    private fun sha256Hex(body: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(body.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /**
     * The Worker's current generation version from the public `/v1/health` route, or
     * [VariationEntity.UNVERSIONED] when the deployed Worker predates versions.
     */
    suspend fun generationVersion(workerUrl: String): Int = withContext(Dispatchers.IO) {
        get("v1/health", workerUrl).optInt("generationVersion", VariationEntity.UNVERSIONED)
    }

    suspend fun habitFields(
        title: String,
        workerUrl: String,
        workerToken: String,
    ): AiHabitFields = withContext(Dispatchers.IO) {
        val body = post("v1/habit-fields", JSONObject().apply { put("title", title) }, workerUrl, workerToken)
        val arr = body.optJSONArray("descriptionLadder")
            ?: throw WorkerError(200, "Missing descriptionLadder in response")
        AiHabitFields(descriptionLadder = (0 until arr.length()).map { arr.getString(it) })
    }

    suspend fun generateBatch(
        habitTitle: String,
        habitTags: List<String>,
        locationName: String,
        timeOfDay: String,
        personalContext: String,
        sprites: List<MascotSprite>,
        supportedModes: Set<ActivityMode>,
        n: Int,
        workerUrl: String,
        workerToken: String,
    ): GeneratedBatch {
        val payload = JSONObject().apply {
            put("habitTitle", habitTitle)
            put("habitTags", JSONArray(habitTags))
            put("locationName", locationName)
            put("timeOfDay", timeOfDay)
            put("personalContext", personalContext)
            put("supportedModes", JSONArray(supportedModes.map { it.name }))
            put("sprites", JSONArray(sprites.map { sprite ->
                JSONObject().apply {
                    put("tag", sprite.tag)
                    put("description", sprite.description)
                }
            }))
            put("n", n)
        }
        return withContext(Dispatchers.IO) {
            val body = post("v1/generate/batch", payload, workerUrl, workerToken)
            val arr = body.optJSONArray("variants")
                ?: throw WorkerError(200, "Missing 'variants' array in response")
            val variants = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                GeneratedVariant(
                    text = obj.getString("text"),
                    shape = obj.getString("shape").let { raw ->
                        VariantShape.entries.firstOrNull { it.name == raw }
                            ?: throw JSONException("Unknown variant shape: $raw")
                    },
                    modes = parseModes(obj.optJSONArray("modes")),
                    actionUrl = obj.optString("actionUrl").takeIf { it.isNotEmpty() },
                    spriteTag = obj.optString("spriteTag").takeIf { it.isNotEmpty() }
                )
            }
            // Absent on a Worker deployed before versions existed; the row is then stamped
            // unversioned so a later versioned refill sweeps it.
            GeneratedBatch(variants, body.optInt("generationVersion", VariationEntity.UNVERSIONED))
        }
    }

    /**
     * Absent on a response from a worker deployed before modes existed, and a mode this build
     * does not know drops out: either way the variant reads as neutral rather than failing
     * the batch, so app and worker can deploy in either order.
     */
    private fun parseModes(arr: JSONArray?): Set<ActivityMode> {
        arr ?: return emptySet()
        return (0 until arr.length())
            .mapNotNull { i -> ActivityMode.entries.firstOrNull { it.name == arr.getString(i) } }
            .toSet()
    }
}
