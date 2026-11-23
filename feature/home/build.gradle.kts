plugins {
    alias(libs.plugins.omniai.android.feature)
}

android {
    namespace = "com.omniai.feature.home"
}

dependencies {
    implementation(projects.core.data)
}
