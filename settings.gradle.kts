pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "OmniAI"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")

// Core (pure JVM)
include(":core:model")
include(":core:common")
include(":core:domain")
include(":core:provider")

// Core (Android)
include(":core:security")
include(":core:database")
include(":core:datastore")
include(":core:data")
include(":core:designsystem")
include(":core:ui")

// Features
include(":feature:home")
include(":feature:chat")
include(":feature:compare")
include(":feature:providers")
include(":feature:models")
include(":feature:prompts")
include(":feature:personas")
include(":feature:usage")
include(":feature:settings")
