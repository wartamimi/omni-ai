plugins {
    alias(libs.plugins.omniai.android.feature)
}

android {
    namespace = "com.omniai.feature.models"
}

dependencies {
    implementation(projects.core.data)
}
