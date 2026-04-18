package com.alexsiri7.unreminder.service.github

import android.util.Base64
import com.alexsiri7.unreminder.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// Forwards feedback submissions to the feedback Cloudflare Worker, which
// holds the GitHub PAT server-side and creates the issue on our behalf.
// The app used to embed a PAT in BuildConfig — that was extractable from
// the signed APK, so it was removed.
@Singleton
class GitHubApiService @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    suspend fun submit(
        title: String,
        body: String,
        screenshotFile: File? = null,
    ): Unit = withContext(Dispatchers.IO) {
        val endpoint = BuildConfig.FEEDBACK_ENDPOINT_URL
        if (endpoint.isBlank()) {
            throw IllegalStateException("FEEDBACK_ENDPOINT_URL not configured")
        }

        // Worker derives the issue title from the first line of message;
        // prepend the title so the issue reads naturally.
        val message = if (title.isNotBlank() && !body.startsWith(title)) {
            "$title\n\n$body"
        } else {
            body
        }

        val screenshotB64 = screenshotFile
            ?.takeIf { it.exists() }
            ?.let { Base64.encodeToString(it.readBytes(), Base64.NO_WRAP) }

        val payload = JSONObject().apply {
            put("repo", BuildConfig.FEEDBACK_REPO)
            put("type", "other")
            put("message", message)
            if (screenshotB64 != null) put("screenshot", screenshotB64)
        }

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Accept", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException(
                    "Feedback submission failed: ${response.code} ${response.body?.string()}"
                )
            }
        }
    }
}
