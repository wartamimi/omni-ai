plugins {
    alias(libs.plugins.omniai.android.library)
    alias(libs.plugins.omniai.android.hilt)
}

android {
    namespace = "com.omniai.core.security"
}

dependencies {
    implementation(projects.core.common)
    implementation(libs.androidx.core.ktx)
    api(libs.androidx.biometric)
    implementation(libs.kotlinx.coroutines.android)
}
