plugins {
    alias(libs.plugins.omniai.android.feature)
}

android {
    namespace = "com.omniai.feature.prompts"
}

dependencies {
    implementation(projects.core.data)
}
