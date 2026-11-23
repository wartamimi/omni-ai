plugins {
    alias(libs.plugins.omniai.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)
    api(projects.core.provider)
    implementation(libs.kotlinx.serialization.json)
}
