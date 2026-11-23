import java.util.Properties

plugins {
    alias(libs.plugins.omniai.android.application)
    alias(libs.plugins.omniai.android.compose)
    alias(libs.plugins.omniai.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing is read from keystore.properties (never committed) or environment variables in CI.
// See docs/SIGNING.md. Without them, release builds are produced unsigned.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? = keystoreProps.getProperty(key) ?: System.getenv(env)

android {
    namespace = "com.omniai.app"

    defaultConfig {
        applicationId = "com.omniai.app"
        versionCode = 1
        versionName = "1.0.0"
    }

    val storeFilePath = signingValue("storeFile", "OMNIAI_KEYSTORE_FILE")
    if (storeFilePath != null) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(storeFilePath)
                storePassword = signingValue("storePassword", "OMNIAI_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "OMNIAI_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "OMNIAI_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (storeFilePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.core.security)

    implementation(projects.feature.home)
    implementation(projects.feature.chat)
    implementation(projects.feature.compare)
    implementation(projects.feature.providers)
    implementation(projects.feature.models)
    implementation(projects.feature.prompts)
    implementation(projects.feature.personas)
    implementation(projects.feature.usage)
    implementation(projects.feature.settings)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit4)
}
