package com.alexsiri7.unreminder.service.llm

import android.content.Context
import android.util.Log
import com.alexsiri7.unreminder.data.db.HabitEntity
import com.alexsiri7.unreminder.domain.model.AiHabitFields
import com.alexsiri7.unreminder.domain.model.LocationTag
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PromptGenerator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "PromptGenerator"
    }

    private var model: GenerativeModel? = null

    suspend fun initialize() {
        try {
            val m = Generation.getClient()
            m.warmup()
            model = m
        } catch (e: Exception) {
            Log.w(TAG, "LLM initialization failed; notification generation will use templates, AI autofill will throw", e)
            model = null
        }
    }

    suspend fun generate(habit: HabitEntity, locationTag: LocationTag, timeOfDay: String): String {
        val m = model ?: return fallback(habit)
        return try {
            withTimeout(5_000) {
                val prompt = buildPrompt(habit, locationTag, timeOfDay)
                val response = m.generateContent(prompt)
                response.candidates.firstOrNull()?.text?.take(80) ?: fallback(habit)
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e  // must propagate: cancellation is not an error
        } catch (e: Exception) {
            Log.w(TAG, "LLM generation failed, using fallback", e)
            fallback(habit)
        }
    }

    private fun buildPrompt(habit: HabitEntity, location: LocationTag, timeOfDay: String): String =
        """System: You are generating a one-line notification that nudges the user to do a habit. Make it warm, specific, and varied — never repeat the exact wording across calls. Maximum 80 characters. Plain text only.
        |
        |Habit: ${habit.name}
        |Full version: ${habit.fullDescription}
        |Low-floor version: ${habit.lowFloorDescription}
        |Current location: ${location.name}
        |Time of day: $timeOfDay""".trimMargin()

    suspend fun generateHabitFields(title: String): AiHabitFields {
        val m = model ?: throw IllegalStateException("LLM unavailable")
        return try {
            withTimeout(5_000) {
                val prompt = buildHabitFieldsPrompt(title)
                val response = m.generateContent(prompt)
                val text = response.candidates.firstOrNull()?.text
                    ?: throw IllegalStateException("Empty LLM response")
                val lines = text.lines()
                val full = lines.firstOrNull { it.startsWith("Full:") }
                    ?.removePrefix("Full:")?.trim()
                    ?: throw IllegalStateException("Could not parse Full: line")
                val low = lines.firstOrNull { it.startsWith("Low-floor:") }
                    ?.removePrefix("Low-floor:")?.trim()
                    ?: throw IllegalStateException("Could not parse Low-floor: line")
                AiHabitFields(full, low)
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e  // must propagate: cancellation is not an error
        } catch (e: Exception) {
            Log.w(TAG, "LLM habit field generation failed", e)
            throw e
        }
    }

    suspend fun previewHabitNotification(habit: HabitEntity, locationTag: LocationTag = LocationTag.ANYWHERE): String {
        val m = model ?: throw IllegalStateException("LLM unavailable")
        return withTimeout(5_000) {
            // "now" tells the LLM to generate a notification appropriate for the current moment
            val prompt = buildPrompt(habit, locationTag, "now")
            val response = m.generateContent(prompt)
            response.candidates.firstOrNull()?.text?.take(80)
                ?: throw IllegalStateException("Empty LLM response")
        }
    }

    private fun buildHabitFieldsPrompt(title: String): String =
        """System: You are generating habit description fields for a productivity app.
        |Given only a habit title, produce exactly two lines:
        |Full: <one sentence, specific full description, max 100 chars>
        |Low-floor: <minimum viable version, max 60 chars>
        |Plain text only.
        |
        |Habit title: $title""".trimMargin()

    private fun fallback(habit: HabitEntity): String =
        "${habit.name}: ${habit.lowFloorDescription}"
}
