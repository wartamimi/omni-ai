plugins {
    alias(libs.plugins.omniai.android.feature)
}

android {
    namespace = "com.omniai.feature.compare"
}

dependencies {
    implementation(projects.core.data)
}
