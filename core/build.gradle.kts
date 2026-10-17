plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
            api("org.jetbrains.compose.runtime:runtime:1.8.2")
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
            api("org.jetbrains.kotlinx:kotlinx-io-core:0.6.0")
        }
        androidMain.dependencies {
            api(project.dependencies.platform("androidx.compose:compose-bom:2025.05.01"))
            api("androidx.activity:activity-compose:1.10.1")
            api("androidx.core:core-ktx:1.16.0")
            api("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
            api("androidx.media3:media3-exoplayer:1.9.0")
            api("androidx.media3:media3-session:1.9.0")
            api("androidx.media3:media3-datasource:1.9.0")
            api("androidx.media3:media3-database:1.9.0")
            api("com.squareup.okhttp3:okhttp:4.12.0")
            api("io.coil-kt:coil-compose:2.7.0")
            api("androidx.palette:palette-ktx:1.0.0")
            api("com.google.guava:guava:33.3.1-android")
            api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
        }
    }
}

android {
    namespace = "com.scd.android.core"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
