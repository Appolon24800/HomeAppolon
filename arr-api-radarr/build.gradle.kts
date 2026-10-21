plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.appolon.homelab.arr.api.radarr"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

android {
    sourceSets {
        getByName("main") {
            kotlin.srcDirs("src/main/kotlin", "src/library/kotlin", "src/library/main/kotlin", "src/discovery/kotlin", "src/discovery/main/kotlin", "src/queue/kotlin", "src/queue/main/kotlin", "src/calendar/kotlin", "src/calendar/main/kotlin", "src/connect/kotlin", "src/connect/main/kotlin", "src/system/kotlin", "src/system/main/kotlin", "src/settings/kotlin", "src/settings/main/kotlin")
            res.srcDirs("src/library/res", "src/library/main/res", "src/discovery/res", "src/discovery/main/res", "src/queue/res", "src/queue/main/res", "src/calendar/res", "src/calendar/main/res", "src/connect/res", "src/connect/main/res", "src/system/res", "src/system/main/res", "src/settings/res", "src/settings/main/res")
        }
        getByName("test") {
            kotlin.srcDirs("src/library/src/test/kotlin", "src/library/test/kotlin", "src/discovery/src/test/kotlin", "src/discovery/test/kotlin", "src/queue/src/test/kotlin", "src/queue/test/kotlin", "src/calendar/src/test/kotlin", "src/calendar/test/kotlin", "src/connect/src/test/kotlin", "src/connect/test/kotlin", "src/system/src/test/kotlin", "src/system/test/kotlin", "src/settings/src/test/kotlin", "src/settings/test/kotlin")
            resources.srcDirs("src/library/src/test/resources", "src/library/test/resources", "src/discovery/src/test/resources", "src/discovery/test/resources", "src/queue/src/test/resources", "src/queue/test/resources", "src/calendar/src/test/resources", "src/calendar/test/resources", "src/connect/src/test/resources", "src/connect/test/resources", "src/system/src/test/resources", "src/system/test/resources", "src/settings/src/test/resources", "src/settings/test/resources")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":arr-net"))
    implementation(project(":arr-core"))
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
