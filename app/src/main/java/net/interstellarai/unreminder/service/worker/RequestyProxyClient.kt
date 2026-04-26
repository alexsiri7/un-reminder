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
    private fun post(path: String, payload: JSONObject, workerUrl: String, secret: String): JSONObject {
        val request = Request.Builder()
            .url("${workerUrl.trimEnd('/')}/$path")
            .addHeader("X-UR-Secret", secret)
            .addHeader("Accept", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return okHttpClient.newCall(request).execute().use { response ->
            if (response.code !in 200..299) response.throwOnError()
            JSONObject(response.body?.string() ?: throw RuntimeException("Worker returned empty body"))
        }
    }

    suspend fun habitFields(
        title: String,
        workerUrl: String,
        secret: String,
    ): AiHabitFields = withContext(Dispatchers.IO) {
        val body = post("v1/habit-fields", JSONObject().apply { put("title", title) }, workerUrl, secret)
        val arr = body.getJSONArray("descriptionLadder")
        AiHabitFields(descriptionLadder = (0 until arr.length()).map { arr.getString(it) })
    }

    suspend fun preview(
        habit: HabitEntity,
        locationName: String,
        workerUrl: String,
        secret: String,
    ): String {
        val habitObj = JSONObject().apply {
            put("title", habit.name)
            // TODO: pass real tags once HabitEntity carries them (currently loaded via junction table)
            put("tags", JSONArray())
            put("notes", habit.descriptionLadder.getOrElse(habit.dedicationLevel) { "" })
        }
        val payload = JSONObject().apply {
            put("habit", habitObj)
            put("locationName", locationName)
        }
        return withContext(Dispatchers.IO) {
            post("v1/preview", payload, workerUrl, secret).getString("text")
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
    ): List<String> {
        val payload = JSONObject().apply {
            put("habitTitle", habitTitle)
            put("habitTags", JSONArray(habitTags))
            put("locationName", locationName)
            put("timeOfDay", timeOfDay)
            put("n", n)
        }
        return withContext(Dispatchers.IO) {
            val arr = post("v1/generate/batch", payload, workerUrl, workerSecret).getJSONArray("variants")
            (0 until arr.length()).map { arr.getString(it) }
        }
    }
}
