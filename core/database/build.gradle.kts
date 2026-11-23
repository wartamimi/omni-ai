plugins {
    alias(libs.plugins.omniai.android.library)
    alias(libs.plugins.omniai.android.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "com.omniai.core.database"
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
}
