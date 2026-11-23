plugins {
    alias(libs.plugins.omniai.android.feature)
}

android {
    namespace = "com.omniai.feature.providers"
}

dependencies {
    implementation(projects.core.data)
}

dependencies {
    implementation(projects.core.security)
}
