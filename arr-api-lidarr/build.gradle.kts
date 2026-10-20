plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.appolon.homelab.arr.api.lidarr"
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
        getByName("main") { kotlin.srcDirs("src/library/kotlin", "src/discovery/kotlin", "src/queue/kotlin", "src/release/kotlin", "src/calendar/kotlin", "src/quality/kotlin", "src/connect/kotlin", "src/system/kotlin", "src/settings/kotlin") }
        getByName("test") {
            kotlin.srcDirs("src/library/src/test/kotlin", "src/discovery/src/test/kotlin", "src/queue/src/test/kotlin", "src/release/src/test/kotlin", "src/calendar/src/test/kotlin", "src/quality/src/test/kotlin", "src/connect/src/test/kotlin", "src/system/src/test/kotlin", "src/settings/src/test/kotlin")
            resources.srcDirs("src/library/src/test/resources", "src/discovery/src/test/resources", "src/queue/src/test/resources", "src/release/src/test/resources", "src/calendar/src/test/resources", "src/quality/src/test/resources", "src/connect/src/test/resources", "src/system/src/test/resources", "src/settings/src/test/resources")
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
