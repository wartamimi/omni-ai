plugins {
    alias(libs.plugins.omniai.android.feature)
}

android {
    namespace = "com.omniai.feature.personas"
}

dependencies {
    implementation(projects.core.data)
}
