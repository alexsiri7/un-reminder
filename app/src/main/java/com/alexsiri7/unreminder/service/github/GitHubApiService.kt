package com.alexsiri7.unreminder.service.github

import android.util.Base64
import android.util.Log
import com.alexsiri7.unreminder.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

class GitHubApiService {

    private val client = OkHttpClient()
    private val token = BuildConfig.GITHUB_FEEDBACK_TOKEN
    private val repo = "alexsiri7/un-reminder"

    companion object {
        private const val TAG = "GitHubApiService"
        private const val API_BASE = "https://api.github.com"
        private const val PAGES_BASE = "https://alexsiri7.github.io/un-reminder"
        private const val SCREENSHOTS_PATH = "docs/feedback-screenshots"
    }

    suspend fun uploadImage(file: File, uuid: String): String? = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext null
        val encoded = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        val body = JSONObject().apply {
            put("message", "Add feedback screenshot $uuid")
            put("content", encoded)
            put("branch", "main")
            put("committer", JSONObject().apply {
                put("name", "Un-Reminder App")
                put("email", "app@un-reminder.local")
            })
        }.toString()
        val request = Request.Builder()
            .url("$API_BASE/repos/$repo/contents/$SCREENSHOTS_PATH/$uuid.png")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .put(body.toRequestBody("application/json".toMediaType()))
            .build()
        return@withContext try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) "$PAGES_BASE/feedback-screenshots/$uuid.png"
                else { Log.e(TAG, "Image upload failed: ${response.code}"); null }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Image upload exception", e)
            null
        }
    }

    suspend fun createIssue(
        description: String,
        imageUrl: String?,
        deviceInfo: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext false
        val title = description.take(60).ifBlank { "Feedback from app" }
        val imageMarkdown = imageUrl?.let { "\n\n![screenshot]($it)" } ?: ""
        val bodyText = "$description$imageMarkdown\n\n---\n$deviceInfo"
        val body = JSONObject().apply {
            put("title", title)
            put("body", bodyText)
            put("labels", org.json.JSONArray().apply { put("feedback") })
        }.toString()
        val request = Request.Builder()
            .url("$API_BASE/repos/$repo/issues")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        return@withContext try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) Log.e(TAG, "Issue creation failed: ${response.code}")
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Issue creation exception", e)
            false
        }
    }
}
