plugins {
    alias(libs.plugins.omniai.jvm.library)
}

dependencies {
    api(projects.core.model)
    api(libs.kotlinx.coroutines.core)
    api(libs.javax.inject)
}
