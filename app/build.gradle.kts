import java.io.StringReader
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// ---- NNCx build identity -------------------------------------------------
// Your identity is not in this repo. Copy nncx.properties.example to
// nncx.local.properties (gitignored) and put your own values there.
//
// Resolution order, first hit wins:
//   nncx.local.properties  ->  -Pnncx.* or gradle.properties  ->  defaults below
//
// Read through providers.fileContents so the configuration cache treats the
// file as an input: edit your dock, and the next build actually sees it.
val nncxLocal: Properties = Properties().apply {
    providers.fileContents(
        rootProject.layout.projectDirectory.file("nncx.local.properties")
    ).asText.orNull?.let { load(StringReader(it)) }
}

fun nncxIdentity(key: String, fallback: String): String =
    nncxLocal.getProperty(key)
        ?: providers.gradleProperty(key).orNull
        ?: fallback

val nncxAccent: String = nncxIdentity("nncx.accent", "#7B2CBF")
val nncxWorkPrefixes: String = nncxIdentity("nncx.work.prefixes", "")
val nncxDock: String = nncxIdentity("nncx.dock", "")
val nncxDockSize: String = nncxIdentity("nncx.dock.size", "5")
val nncxDockSide: String = nncxIdentity("nncx.dock.side", "right")

android {
    namespace = "dev.nncx"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.nncx"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "ACCENT", "\"$nncxAccent\"")
        buildConfigField("String", "WORK_PREFIXES", "\"$nncxWorkPrefixes\"")
        buildConfigField("String", "DOCK", "\"$nncxDock\"")
        buildConfigField("int", "DOCK_SIZE", nncxDockSize)
        buildConfigField("String", "DOCK_SIDE", "\"$nncxDockSide\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            // Keep the package name stable so `adb` and the launcher role
            // don't change identity between build types.
            applicationIdSuffix = null
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.window)
    implementation(libs.adaptive)
    implementation(libs.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
