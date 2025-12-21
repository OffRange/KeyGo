enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
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
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "KeyGoV2"
include(":app")
include(":automation-processor")
include(":automation")
include(":migration-create-access")
include(":core:item")
include(":core:util")
include(":core:security")
include(":core:ui")
include(":rust")
include(":feature:credentials")
include(":feature:list_screen")
include(":feature:item:core")
include(":feature:item:create")
include(":feature:totp")
