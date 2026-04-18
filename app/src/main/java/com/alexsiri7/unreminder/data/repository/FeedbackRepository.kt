package com.alexsiri7.unreminder.data.repository

import com.alexsiri7.unreminder.data.db.PendingFeedbackDao
import com.alexsiri7.unreminder.data.db.PendingFeedbackEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedbackRepository @Inject constructor(
    private val pendingFeedbackDao: PendingFeedbackDao
) {
    suspend fun queue(screenshotPath: String?, description: String): Long =
        pendingFeedbackDao.insert(PendingFeedbackEntity(screenshotPath = screenshotPath, description = description))

    suspend fun getPending(): List<PendingFeedbackEntity> = pendingFeedbackDao.getAll()

    suspend fun deleteById(id: Long) = pendingFeedbackDao.deleteById(id)
}
