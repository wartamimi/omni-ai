plugins {
    alias(libs.plugins.omniai.android.library)
    alias(libs.plugins.omniai.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.omniai.core.datastore"
}

dependencies {
    implementation(projects.core.common)
    api(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
}
