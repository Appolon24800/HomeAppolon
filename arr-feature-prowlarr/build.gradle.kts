plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.appolon.homelab.arr.feature.prowlarr"
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
            kotlin.srcDirs("src/main/kotlin", "src/indexers/kotlin", "src/indexers/main/kotlin", "src/search/kotlin", "src/search/main/kotlin", "src/stats/kotlin", "src/stats/main/kotlin", "src/history/kotlin", "src/history/main/kotlin", "src/settings/kotlin", "src/settings/main/kotlin")
            res.srcDirs("src/indexers/res", "src/indexers/main/res", "src/search/res", "src/search/main/res", "src/stats/res", "src/stats/main/res", "src/history/res", "src/history/main/res", "src/settings/res", "src/settings/main/res")
        }
        getByName("test") {
            kotlin.srcDirs("src/indexers/src/test/kotlin", "src/indexers/test/kotlin", "src/search/src/test/kotlin", "src/search/test/kotlin", "src/stats/src/test/kotlin", "src/stats/test/kotlin", "src/history/src/test/kotlin", "src/history/test/kotlin", "src/settings/src/test/kotlin", "src/settings/test/kotlin")
            resources.srcDirs("src/indexers/src/test/resources", "src/indexers/test/resources", "src/search/src/test/resources", "src/search/test/resources", "src/stats/src/test/resources", "src/stats/test/resources", "src/history/src/test/resources", "src/history/test/resources", "src/settings/src/test/resources", "src/settings/test/resources")
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
    implementation(project(":arr-sync"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.coil.network.okhttp)
    implementation(project(":arr-ui-core"))
    implementation(project(":arr-api-prowlarr"))
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
