plugins {
    alias(libs.plugins.omniai.android.library)
    alias(libs.plugins.omniai.android.compose)
}

android {
    namespace = "com.omniai.core.ui"
}

dependencies {
    api(projects.core.designsystem)
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(libs.androidx.core.ktx)
    implementation(libs.markdown.renderer.m3)
    implementation(libs.markdown.renderer.code)
}
