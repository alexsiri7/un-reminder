package net.interstellarai.unreminder.di

import android.content.Context
import net.interstellarai.unreminder.data.repository.LocationRepository
import net.interstellarai.unreminder.service.worker.RefillScheduler
import net.interstellarai.unreminder.service.geofence.GeofenceManager
import net.interstellarai.unreminder.service.llm.CloudPromptGenerator
import net.interstellarai.unreminder.service.llm.PromptGenerator
import net.interstellarai.unreminder.service.notification.EmojiRotator
import net.interstellarai.unreminder.service.notification.NotificationHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

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
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRefillScheduler(@ApplicationContext context: Context): RefillScheduler =
        RefillScheduler(context)

    @Provides
    @Singleton
    fun providePromptGenerator(
        promptGenerator: CloudPromptGenerator,
    ): PromptGenerator = promptGenerator

}
