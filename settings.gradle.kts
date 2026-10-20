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
    }
}
rootProject.name = "Homelab"
include(":app")
include(":arr-core")
include(":arr-net")
include(":arr-data")
include(":arr-ui-core")
include(":arr-api-radarr")
include(":arr-api-sonarr")
include(":arr-api-lidarr")
include(":arr-api-prowlarr")
include(":arr-api-bazarr")
include(":arr-feature-radarr")
include(":arr-feature-sonarr")
include(":arr-feature-lidarr")
include(":arr-feature-prowlarr")
include(":arr-feature-bazarr")
include(":arr-feature-servers")
include(":arr-sync")
