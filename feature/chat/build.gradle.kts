plugins {
    alias(libs.plugins.omniai.android.feature)
}

android {
    namespace = "com.omniai.feature.chat"
}

dependencies {
    implementation(projects.core.data)
}

dependencies {
    implementation(libs.coil.compose)
}
