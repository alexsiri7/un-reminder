package net.interstellarai.unreminder.di

import javax.inject.Qualifier

/** The cloud Worker's base URL baked in at build time; blank when the build has none. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WorkerUrl
