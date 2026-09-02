import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.jarvis"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jarvis"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"

        buildConfigField("String", "BACKEND_WS_URL", "\"wss://jarvis-android.onrender.com/ws\"")
        buildConfigField("String", "BACKEND_API_URL", "\"https://jarvis-android.onrender.com\"")

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        create("release") {
            val ksBase64 = System.getenv("KEYSTORE_BASE64")
            val ksPassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            val keyAlias = System.getenv("KEY_ALIAS") ?: ""
            val keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            if (ksBase64 != null && ksBase64.isNotEmpty()) {
                val ksFile = project.file("release.jks")
                val bytes = Base64.getDecoder().decode(ksBase64)
                ksFile.writeBytes(bytes)
                storeFile = ksFile
                storePassword = ksPassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            val ksBase64 = System.getenv("KEYSTORE_BASE64")
            signingConfig = if (ksBase64 != null && ksBase64.isNotEmpty()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/*.version",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module",
                "**/DebugProbesKt.bin"
            )
        }
    }

    lint {
        checkReleaseBuilds = true
        abortOnError = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    aaptOptions {
        noCompress("onnx")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ONNX Runtime Mobile — runs offline wake-word models (melspectrogram, embedding_model, hey_jarvis)
    // entirely on-device with zero cloud dependencies.
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")

    // Encrypted storage for auth tokens
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Reminders: WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Memory: Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
