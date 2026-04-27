# Add project specific ProGuard rules here.

# Hilt / Dagger
-keep,allowobfuscation @interface dagger.hilt.android.AndroidEntryPoint
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-dontwarn dagger.hilt.**

# HiltViewModel — ViewModel subclasses are instantiated via reflection
-keepclassmembers,allowobfuscation class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# HiltWorker — Workers are instantiated via reflection by WorkManager
-keep class * extends androidx.work.Worker { <init>(...); }
-keep class * extends androidx.work.CoroutineWorker { <init>(...); }
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

# Room
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Entity class * { *; }
-keepclassmembers @androidx.room.Entity class * { *; }
-keep @androidx.room.TypeConverters class * { *; }
-dontwarn androidx.room.**

# Sentry
-keep class io.sentry.** { *; }
-dontwarn io.sentry.**
