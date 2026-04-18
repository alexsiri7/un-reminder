package com.alexsiri7.unreminder.di

import android.content.Context
import com.alexsiri7.unreminder.data.repository.LocationRepository
import com.alexsiri7.unreminder.service.alarm.AlarmScheduler
import com.alexsiri7.unreminder.service.geofence.GeofenceManager
import com.alexsiri7.unreminder.service.notification.EmojiRotator
import com.alexsiri7.unreminder.service.notification.NotificationHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    fun provideAlarmScheduler(@ApplicationContext context: Context): AlarmScheduler {
        return AlarmScheduler(context)
    }

    @Provides
    @Singleton
    fun provideGeofenceManager(
        @ApplicationContext context: Context,
        locationRepository: LocationRepository
    ): GeofenceManager {
        return GeofenceManager(context, locationRepository)
    }

    @Provides
    @Singleton
    fun provideNotificationHelper(@ApplicationContext context: Context, emojiRotator: EmojiRotator): NotificationHelper {
        return NotificationHelper(context, emojiRotator)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()
}
