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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubApiService @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val REPO = "alexsiri7/un-reminder"
        private const val BRANCH = "main"
        private const val SCREENSHOTS_PATH = "docs/feedback-screenshots"
        private const val PAGES_BASE = "https://alexsiri7.github.io/un-reminder/feedback-screenshots"
    }

    suspend fun uploadImage(imageFile: File): String = withContext(Dispatchers.IO) {
        val uuid = UUID.randomUUID().toString()
        val fileName = "$uuid.png"
        val base64Content = Base64.encodeToString(imageFile.readBytes(), Base64.NO_WRAP)

        val json = JSONObject().apply {
            put("message", "Add feedback screenshot $uuid")
            put("content", base64Content)
            put("branch", BRANCH)
        }

        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO/contents/$SCREENSHOTS_PATH/$fileName")
            .addHeader("Authorization", "Bearer ${BuildConfig.GITHUB_FEEDBACK_TOKEN}")
            .addHeader("Accept", "application/vnd.github+json")
            .put(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Image upload failed: ${response.code} ${response.body?.string()}")
            }
        }
        "$PAGES_BASE/$fileName"
    }

    suspend fun createIssue(title: String, body: String) = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("title", title)
            put("body", body)
            put("labels", org.json.JSONArray().put("feedback"))
        }

        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO/issues")
            .addHeader("Authorization", "Bearer ${BuildConfig.GITHUB_FEEDBACK_TOKEN}")
            .addHeader("Accept", "application/vnd.github+json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Issue creation failed: ${response.code} ${response.body?.string()}")
            }
        }
    }
}
