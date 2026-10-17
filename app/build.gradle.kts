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
        versionCode = 43
        versionName = "2.5.1"
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
    // Bundled yt-dlp/Python runtime: in-app extraction for public social video URLs, no external API server.
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("com.arthenica:smart-exception-java:0.2.1")
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
}