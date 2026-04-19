package net.interstellarai.unreminder.di

import android.content.Context
import net.interstellarai.unreminder.BuildConfig
import net.interstellarai.unreminder.data.repository.LocationRepository
import net.interstellarai.unreminder.service.alarm.AlarmScheduler
import net.interstellarai.unreminder.service.geofence.GeofenceManager
import net.interstellarai.unreminder.service.llm.PromptGenerator
import net.interstellarai.unreminder.service.llm.PromptGeneratorImpl
import net.interstellarai.unreminder.service.notification.EmojiRotator
import net.interstellarai.unreminder.service.notification.NotificationHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    fun provideAlarmScheduler(@ApplicationContext context: Context): AlarmScheduler =
        AlarmScheduler(context)

    @Provides
    @Singleton
    fun provideGeofenceManager(
        @ApplicationContext context: Context,
        locationRepository: LocationRepository
    ): GeofenceManager = GeofenceManager(context, locationRepository)

    @Provides
    @Singleton
    fun provideNotificationHelper(
        @ApplicationContext context: Context,
        emojiRotator: EmojiRotator
    ): NotificationHelper = NotificationHelper(context, emojiRotator)

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

    @Provides
    @Singleton
    fun providePromptGenerator(@ApplicationContext context: Context): PromptGenerator =
        PromptGeneratorImpl(context)

    @Provides
    @Named("modelCdnUrl")
    fun provideModelCdnUrl(): String = BuildConfig.MODEL_CDN_URL
}
