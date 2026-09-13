package net.interstellarai.unreminder.service.worker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.interstellarai.unreminder.data.db.VariationEntity
import net.interstellarai.unreminder.domain.model.ActivityMode
import net.interstellarai.unreminder.domain.model.AiHabitFields
import net.interstellarai.unreminder.domain.model.GeneratedBatch
import net.interstellarai.unreminder.domain.model.GeneratedVariant
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
import javax.inject.Inject
import javax.inject.Singleton

private fun Response.throwOnError(): Nothing = when (code) {
    401 -> throw WorkerAuthException()
    402 -> throw SpendCapExceededException()
    else -> throw WorkerError(code, body?.string() ?: "")
}

@Singleton
class RequestyProxyClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private fun post(path: String, payload: JSONObject, workerUrl: String, secret: String): JSONObject =
        execute(
            Request.Builder()
                .url("${workerUrl.trimEnd('/')}/$path")
                .addHeader("X-UR-Secret", secret)
                .addHeader("Accept", "application/json")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
        )

    private fun get(path: String, workerUrl: String): JSONObject =
        execute(
            Request.Builder()
                .url("${workerUrl.trimEnd('/')}/$path")
                .addHeader("Accept", "application/json")
                .get()
                .build()
        )

    private fun execute(request: Request): JSONObject =
        okHttpClient.newCall(request).execute().use { response ->
            if (response.code !in 200..299) response.throwOnError()
            JSONObject(response.body?.string() ?: throw RuntimeException("Worker returned empty body"))
        }

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
        secret: String,
    ): AiHabitFields = withContext(Dispatchers.IO) {
        val body = post("v1/habit-fields", JSONObject().apply { put("title", title) }, workerUrl, secret)
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
        workerSecret: String,
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
            val body = post("v1/generate/batch", payload, workerUrl, workerSecret)
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
