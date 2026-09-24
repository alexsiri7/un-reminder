package net.interstellarai.unreminder.di

import javax.inject.Qualifier

/** Short human label for this device, sent when self-registering; see deviceLabel(). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DeviceLabel
