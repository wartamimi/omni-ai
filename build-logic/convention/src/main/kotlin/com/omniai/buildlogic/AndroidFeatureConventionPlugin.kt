package com.omniai.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** A feature module: Android library + Compose + Hilt + the shared core modules. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("omniai.android.library")
            pluginManager.apply("omniai.android.compose")
            pluginManager.apply("omniai.android.hilt")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
            dependencies {
                add("implementation", project(":core:model"))
                add("implementation", project(":core:common"))
                add("implementation", project(":core:domain"))
                add("implementation", project(":core:designsystem"))
                add("implementation", project(":core:ui"))
                add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
                add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
                add("implementation", libs.lib("androidx-hilt-navigation-compose"))
                add("implementation", libs.lib("androidx-navigation-compose"))
                add("implementation", libs.lib("kotlinx-serialization-json"))
                add("implementation", libs.lib("kotlinx-coroutines-android"))
            }
        }
    }
}
