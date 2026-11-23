package com.omniai.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            extensions.configure<ApplicationExtension> {
                compileSdk = OmniSdk.COMPILE
                defaultConfig {
                    minSdk = OmniSdk.MIN
                    targetSdk = OmniSdk.TARGET
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    vectorDrawables { useSupportLibrary = true }
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                testOptions {
                    unitTests.isIncludeAndroidResources = true
                    unitTests.isReturnDefaultValues = true
                }
                packaging {
                    resources.excludes += setOf(
                        "/META-INF/{AL2.0,LGPL2.1}",
                        "/META-INF/LICENSE*",
                        "/META-INF/NOTICE*",
                        "/META-INF/*.kotlin_module",
                    )
                }
            }
        }
    }
}
