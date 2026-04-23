package net.interstellarai.unreminder.service.worker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.domain.model.AiHabitFields
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
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
    suspend fun habitFields(
        title: String,
        workerUrl: String,
        secret: String,
    ): AiHabitFields = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply { put("title", title) }
        val request = Request.Builder()
            .url("${workerUrl.trimEnd('/')}/v1/habit-fields")
            .addHeader("X-UR-Secret", secret)
            .addHeader("Accept", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (response.code !in 200..299) response.throwOnError()
            val rawBody = response.body?.string()
                ?: throw RuntimeException("Worker returned empty body")
            val body = JSONObject(rawBody)
            val full = body.getString("fullDescription")
            val lowFloor = body.getString("lowFloorDescription")
            // Worker still returns the legacy binary format (fullDescription / lowFloorDescription).
            // Map to the 6-level ladder: low-floor anchors level 0, full anchors level 5;
            // intermediate levels 1–4 are blank and can be filled in the editor.
            AiHabitFields(
                levelDescriptions = listOf(lowFloor, "", "", "", "", full)
            )
        }
    }

    suspend fun preview(
        habit: HabitEntity,
        notes: String,
        locationName: String,
        workerUrl: String,
        secret: String,
    ): String = withContext(Dispatchers.IO) {
        val habitObj = JSONObject().apply {
            put("title", habit.name)
            // TODO: pass real tags once HabitEntity carries them (currently loaded via junction table)
            put("tags", JSONArray())
            put("notes", notes)
        }
        val payload = JSONObject().apply {
            put("habit", habitObj)
            put("locationName", locationName)
        }
        val request = Request.Builder()
            .url("${workerUrl.trimEnd('/')}/v1/preview")
            .addHeader("X-UR-Secret", secret)
            .addHeader("Accept", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (response.code !in 200..299) response.throwOnError()
            val rawBody = response.body?.string()
                ?: throw RuntimeException("Worker returned empty body")
            JSONObject(rawBody).getString("text")
        }
    }

    suspend fun generateBatch(
        habitTitle: String,
        habitTags: List<String>,
        locationName: String,
        timeOfDay: String,
        n: Int,
        workerUrl: String,
        workerSecret: String,
    ): List<String> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("habitTitle", habitTitle)
            put("habitTags", JSONArray(habitTags))
            put("locationName", locationName)
            put("timeOfDay", timeOfDay)
            put("n", n)
        }
        val request = Request.Builder()
            .url("${workerUrl.trimEnd('/')}/v1/generate/batch")
            .addHeader("X-UR-Secret", workerSecret)
            .addHeader("Accept", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (response.code !in 200..299) response.throwOnError()
            val rawBody = response.body?.string()
                ?: throw RuntimeException("Worker returned empty body")
            val arr = JSONObject(rawBody).getJSONArray("variants")
            (0 until arr.length()).map { arr.getString(it) }
        }
    }
}
