plugins {
    alias(libs.plugins.omniai.android.library)
    alias(libs.plugins.omniai.android.compose)
}

android {
    namespace = "com.omniai.core.designsystem"
}

dependencies {
    api(projects.core.model)
    implementation(libs.androidx.core.ktx)
}
