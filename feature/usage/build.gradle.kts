plugins {
    alias(libs.plugins.omniai.android.feature)
}

android {
    namespace = "com.omniai.feature.usage"
}

dependencies {
    implementation(projects.core.data)
}
