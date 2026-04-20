package net.interstellarai.unreminder.service.worker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.domain.model.AiHabitFields
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

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
            when (response.code) {
                401 -> throw WorkerAuthException()
                402 -> throw SpendCapExceededException()
                in 200..299 -> {
                    val rawBody = response.body?.string()
                        ?: throw RuntimeException("Worker returned empty body")
                    val body = JSONObject(rawBody)
                    AiHabitFields(
                        fullDescription = body.getString("fullDescription"),
                        lowFloorDescription = body.getString("lowFloorDescription"),
                    )
                }
                else -> {
                    val body = response.body?.string() ?: ""
                    throw RuntimeException("Worker error ${response.code}: $body")
                }
            }
        }
    }

    suspend fun preview(
        habit: HabitEntity,
        locationName: String,
        workerUrl: String,
        secret: String,
    ): String = withContext(Dispatchers.IO) {
        val habitObj = JSONObject().apply {
            put("title", habit.name)
            put("tags", JSONArray())
            put("notes", habit.fullDescription)
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
            when (response.code) {
                401 -> throw WorkerAuthException()
                402 -> throw SpendCapExceededException()
                in 200..299 -> {
                    val rawBody = response.body?.string()
                        ?: throw RuntimeException("Worker returned empty body")
                    val body = JSONObject(rawBody)
                    body.getString("text")
                }
                else -> {
                    val body = response.body?.string() ?: ""
                    throw RuntimeException("Worker error ${response.code}: $body")
                }
            }
        }
    }
}
