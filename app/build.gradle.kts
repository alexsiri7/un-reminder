plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.sentry)
}

// Reads an environment variable via gradle's `providers` API so the lookup is
// tracked by the configuration cache (plain `System.getenv` is not tracked and
// can cause the cached value to be reused across CI builds). Also treats a
// blank/empty string as "unset" so `?:` style fallbacks actually fire when the
// env var exists but was exported as "" — otherwise BuildConfig ends up with a
// literal empty string. Bug history: `BuildConfig.MODEL_CDN_URL` was resolving
// to "" at runtime even though CI logs showed `MODEL_CDN_URL: ***` masked.
fun envOrDefault(name: String, default: String): String =
    providers.environmentVariable(name).orNull
        ?.takeUnless { it.isBlank() }
        ?: default

android {
    namespace = "net.interstellarai.unreminder"
    compileSdk = 35

    defaultConfig {
        applicationId = "net.interstellarai.unreminder"
        minSdk = 31
        targetSdk = 34
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val resolvedFeedbackUrl = envOrDefault(
            "FEEDBACK_ENDPOINT_URL",
            "https://feedback.alexsiri7.workers.dev/"
        )
        val resolvedSentryDsn = envOrDefault("SENTRY_DSN", "")
        val resolvedModelUrl = envOrDefault(
            "MODEL_CDN_URL",
            "https://placeholder.invalid/model.task"
        )

        println("[gradle] FEEDBACK_ENDPOINT_URL resolved at configuration: ${resolvedFeedbackUrl.take(60)}…")
        println("[gradle] SENTRY_DSN resolved at configuration: ${if (resolvedSentryDsn.isBlank()) "empty" else "set"}")
        println("[gradle] MODEL_CDN_URL resolved at configuration: ${resolvedModelUrl.take(60)}…")

        buildConfigField(
            "String",
            "FEEDBACK_ENDPOINT_URL",
            "\"${resolvedFeedbackUrl}\""
        )
        buildConfigField(
            "String",
            "FEEDBACK_REPO",
            "\"alexsiri7/un-reminder\""
        )
        buildConfigField("String", "SENTRY_DSN", "\"${resolvedSentryDsn}\"")
        buildConfigField(
            "String",
            "MODEL_CDN_URL",
            "\"${resolvedModelUrl}\""
        )

    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-Xskip-metadata-version-check"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

sentry {
    autoInstallation { enabled.set(false) }
    org.set("alex-siri")
    projectName.set("un-reminder")
    includeProguardMapping.set(false)
    includeSourceContext.set(true)
    autoUploadProguardMapping.set(false)
    autoUploadSourceContext.set(!System.getenv("SENTRY_AUTH_TOKEN").isNullOrBlank())
    uploadNativeSymbols.set(false)
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // OkHttp
    implementation(libs.okhttp)

    // Play Services Location (geofencing)
    implementation(libs.play.services.location)
    implementation(libs.osmdroid.android)

    // LiteRT-LM (on-device Gemma)
    implementation(libs.litert.lm)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Sentry
    implementation(libs.sentry.android)

    // Coroutines
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
