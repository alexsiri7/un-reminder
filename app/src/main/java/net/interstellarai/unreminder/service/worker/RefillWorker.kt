package net.interstellarai.unreminder.service.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import net.interstellarai.unreminder.data.db.VariationEntity
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.VariationRepository
import net.interstellarai.unreminder.data.repository.WorkerSettingsRepository
import java.io.IOException
import java.time.Instant

@HiltWorker
class RefillWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val habitRepository: HabitRepository,
    private val variationRepository: VariationRepository,
    private val requestyProxyClient: RequestyProxyClient,
    private val workerSettingsRepository: WorkerSettingsRepository,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "refill"
        const val KEY_HABIT_ID = "habit_id"
        private const val TAG = "RefillWorker"
    }

    override suspend fun doWork(): Result {
        val habitId = inputData.getLong(KEY_HABIT_ID, -1L)
        if (habitId == -1L) {
            Log.w(TAG, "No habit_id in input data")
            return Result.failure()
        }

        val url = workerSettingsRepository.workerUrl.first()
        if (url.isBlank()) {
            Log.w(TAG, "Worker URL is blank, skipping refill for habit $habitId")
            return Result.failure()
        }
        val secret = workerSettingsRepository.workerSecret.first()
        if (secret.isBlank()) {
            Log.w(TAG, "Worker secret is blank, skipping refill for habit $habitId")
            return Result.failure()
        }

        val habit = habitRepository.getByIdOnce(habitId)
        if (habit == null) {
            Log.w(TAG, "Habit $habitId not found, skipping refill")
            return Result.failure()
        }

        val promptFingerprint = "${habit.name}|${habit.levelDescriptions.joinToString("|")}"

        return try {
            val texts = requestyProxyClient.generateBatch(
                habitTitle = habit.name,
                habitTags = emptyList(),
                locationName = "",
                timeOfDay = "",
                n = 20,
                workerUrl = url,
                workerSecret = secret,
            )
            val now = Instant.now()
            val entities = texts.map { text ->
                VariationEntity(
                    habitId = habitId,
                    text = text,
                    promptFingerprint = promptFingerprint,
                    generatedAt = now,
                )
            }
            variationRepository.insertAll(entities)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SpendCapExceededException) {
            Log.w(TAG, "Spend cap exceeded for habit $habitId", e)
            Result.failure()
        } catch (e: WorkerAuthException) {
            Log.w(TAG, "Auth failed for habit $habitId", e)
            Result.failure()
        } catch (e: WorkerError) {
            if (e.code in 500..599) {
                Log.w(TAG, "Server error ${e.code} for habit $habitId, will retry", e)
                Result.retry()
            } else {
                Log.w(TAG, "Client error ${e.code} for habit $habitId", e)
                Result.failure()
            }
        } catch (e: IOException) {
            Log.w(TAG, "IO error for habit $habitId, will retry", e)
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error for habit $habitId", e)
            Result.failure()
        }
    }
}
