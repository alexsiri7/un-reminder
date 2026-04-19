package net.interstellarai.unreminder.di

import android.content.Context
import net.interstellarai.unreminder.data.repository.ActiveModelRepository
import net.interstellarai.unreminder.data.repository.LocationRepository
import net.interstellarai.unreminder.data.repository.ModelDownloadProgressRepository
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
    fun providePromptGenerator(
        @ApplicationContext context: Context,
        modelDownloadProgressRepository: ModelDownloadProgressRepository,
        activeModelRepository: ActiveModelRepository,
    ): PromptGenerator =
        PromptGeneratorImpl(
            context = context,
            progressRepository = modelDownloadProgressRepository,
            activeModelRepository = activeModelRepository,
        )

    // Note: the old `@Named("modelCdnUrl")` binding for `BuildConfig.MODEL_CDN_URL`
    // has been deleted. The model URL is now looked up via the catalog
    // (`ModelCatalog.byId(...).url`) keyed on the user's active-model selection.
    // BuildConfig.MODEL_CDN_URL remains compiled in but unread — left in place
    // so the CI secret doesn't have to be rotated just for this refactor.
}
