plugins {
    alias(libs.plugins.omniai.android.library)
    alias(libs.plugins.omniai.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.omniai.core.data"
}

dependencies {
    api(projects.core.domain)
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.provider)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.security)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.pdfbox.android)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
