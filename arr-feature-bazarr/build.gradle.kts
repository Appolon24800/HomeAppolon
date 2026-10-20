plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.appolon.homelab.arr.feature.bazarr"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets {
        getByName("main") {
            kotlin.srcDirs("src/series/kotlin", "src/episodes/kotlin", "src/wanted/kotlin", "src/history/kotlin", "src/settings/kotlin")
            res.srcDirs("src/series/res", "src/episodes/res", "src/wanted/res", "src/history/res", "src/settings/res")
        }
        getByName("test") {
            kotlin.srcDirs("src/series/src/test/kotlin", "src/episodes/src/test/kotlin", "src/wanted/src/test/kotlin", "src/history/src/test/kotlin", "src/settings/src/test/kotlin")
            resources.srcDirs("src/series/src/test/resources", "src/episodes/src/test/resources", "src/wanted/src/test/resources", "src/history/src/test/resources", "src/settings/src/test/resources")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":arr-core"))
    implementation(project(":arr-data"))
    implementation(project(":arr-net"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.coil.network.okhttp)
    implementation(project(":arr-ui-core"))
    implementation(project(":arr-api-bazarr"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.tooling.preview)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
