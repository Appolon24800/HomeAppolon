plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.appolon.homelab.arr.api.prowlarr"
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
            kotlin.srcDirs("src/main/kotlin", "src/indexers/kotlin", "src/indexers/main/kotlin", "src/search/kotlin", "src/search/main/kotlin", "src/settings/kotlin", "src/settings/main/kotlin", "src/system/kotlin", "src/system/main/kotlin")
            res.srcDirs("src/indexers/res", "src/indexers/main/res", "src/search/res", "src/search/main/res", "src/settings/res", "src/settings/main/res", "src/system/res", "src/system/main/res")
        }
        getByName("test") {
            kotlin.srcDirs("src/indexers/src/test/kotlin", "src/indexers/test/kotlin", "src/search/src/test/kotlin", "src/search/test/kotlin", "src/settings/src/test/kotlin", "src/settings/test/kotlin", "src/system/src/test/kotlin", "src/system/test/kotlin")
            resources.srcDirs("src/indexers/src/test/resources", "src/indexers/test/resources", "src/search/src/test/resources", "src/search/test/resources", "src/settings/src/test/resources", "src/settings/test/resources", "src/system/src/test/resources", "src/system/test/resources")
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
