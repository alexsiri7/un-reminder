# Add project specific ProGuard rules here.

# Hilt / Dagger
-keep,allowobfuscation @interface dagger.hilt.android.AndroidEntryPoint
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keep class dagger.hilt.** { *; }
# javax.inject.** removed — @Inject annotations are compile-time only (KSP), not accessed at runtime
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
# Keep @TypeConverter-annotated methods — Converters class is plain (no class-level annotation),
# so this member-level rule is required; the class-level rule above matches AppDatabase, not Converters.
-keepclassmembers class * {
    @androidx.room.TypeConverter <methods>;
}
-dontwarn androidx.room.**

# Sentry — consumer rules in the Sentry AAR (io.sentry:sentry-android-core) cover the required
# keep rules; the broad wildcard below was defeating R8 shrinking across the entire SDK.
# -dontwarn is retained to suppress notes on optional Sentry dependencies.
-dontwarn io.sentry.**
