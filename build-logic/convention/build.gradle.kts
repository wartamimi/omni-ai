plugins {
    `kotlin-dsl`
}

group = "com.omniai.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "omniai.android.application"
            implementationClass = "com.omniai.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "omniai.android.library"
            implementationClass = "com.omniai.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "omniai.android.compose"
            implementationClass = "com.omniai.buildlogic.AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "omniai.android.feature"
            implementationClass = "com.omniai.buildlogic.AndroidFeatureConventionPlugin"
        }
        register("androidHilt") {
            id = "omniai.android.hilt"
            implementationClass = "com.omniai.buildlogic.AndroidHiltConventionPlugin"
        }
        register("jvmLibrary") {
            id = "omniai.jvm.library"
            implementationClass = "com.omniai.buildlogic.JvmLibraryConventionPlugin"
        }
    }
}
