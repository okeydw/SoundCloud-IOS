import org.gradle.api.tasks.Sync

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "SoundCloudUi"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            api(compose.animation)
            api(compose.components.resources)
            api("dev.chrisbanes.haze:haze:1.6.0")
            api("io.coil-kt.coil3:coil-compose:3.2.0")
            api("sh.calvin.reorderable:reorderable:2.4.3")
            api("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
        }
        androidMain.dependencies {
            api("com.journeyapps:zxing-android-embedded:4.3.0")
        }
    }
}

android {
    namespace = "com.scd.android.ui"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

val composeResDir = layout.buildDirectory.dir("composeResSource")

val syncComposeRes by tasks.registering(Sync::class) {
    val root = rootProject.layout.projectDirectory
    into(composeResDir)
    from(root.dir("core/src/androidMain/res")) {
        include("values*/strings.xml", "values*/strings_full.xml", "drawable/*.xml")
    }
    from(layout.projectDirectory.dir("fonts")) {
        include("*.ttf")
        into("font")
    }
    filesMatching("values*/*.xml") {
        filter { line: String ->
            var l = line.replace("\\'", "'").replace("\\\"", "\"")
            if (Regex("%[sd]").findAll(l).count() == 1 && !l.contains("%1$")) l = l.replace("%d", "%1\$d").replace("%s", "%1\$s")
            l
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.scd.android.res"
    generateResClass = always
    customDirectory("commonMain", composeResDir)
}

tasks.configureEach {
    if (name != "syncComposeRes" && (name.contains("ComposeResources") || name.contains("ResourceAccessors") || name.contains("ResourceCollectors") || name.contains("ValueResources"))) {
        dependsOn(syncComposeRes)
    }
}
