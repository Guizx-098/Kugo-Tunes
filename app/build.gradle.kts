plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.sumanth.spd"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "dev.sumanth.spd"
        minSdk = 24
        targetSdk = 36
        versionCode = 47
        versionName = "2.6.4"
        // yt-dlp + ffmpeg are bundled per CPU type: phones only (no emulator x86) keeps the APK smaller
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    packaging {
        jniLibs {
            // yt-dlp's Python runtime is unpacked from native libs at first launch
            useLegacyPackaging = true
            pickFirsts += "**/libc++_shared.so"
        }
        resources {
            // Only license / notice texts of the libraries (services and other metadata are kept)
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}", "/META-INF/DEPENDENCIES", "/META-INF/LICENSE*",
                "/META-INF/NOTICE*", "/META-INF/INDEX.LIST"
            )
        }
    }

    signingConfigs {
        // Fixed key: every release must be signed with the same certificate, otherwise Android refuses in-app updates
        create("release") {
            storeFile = file("spd-turbo-release.p12")
            storeType = "pkcs12"
            storePassword = "spdturbo2026"
            keyAlias = "spdturbo"
            keyPassword = "spdturbo2026"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.1")
    implementation("androidx.documentfile:documentfile:1.1.0")

    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.26.5")
    implementation("com.arthenica:smart-exception-java:0.2.1")
    // yt-dlp for Android (GPL-3.0): TikTok, Instagram Reels and Facebook extraction
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
}