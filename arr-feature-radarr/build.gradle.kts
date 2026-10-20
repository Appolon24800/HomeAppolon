plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.appolon.homelab.arr.feature.radarr"
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
            kotlin.srcDirs("src/library/kotlin", "src/detail/kotlin", "src/addnew/kotlin", "src/wanted/kotlin", "src/queue/kotlin", "src/activity/kotlin", "src/calendar/kotlin", "src/search/kotlin", "src/settings-mm/kotlin", "src/settings-quality/kotlin", "src/settings-connect/kotlin", "src/settings-system/kotlin")
            res.srcDirs("src/library/res", "src/detail/res", "src/addnew/res", "src/wanted/res", "src/queue/res", "src/activity/res", "src/calendar/res", "src/search/res", "src/settings-mm/res", "src/settings-quality/res", "src/settings-connect/res", "src/settings-system/res")
        }
        getByName("test") {
            kotlin.srcDirs("src/library/src/test/kotlin", "src/detail/src/test/kotlin", "src/addnew/src/test/kotlin", "src/wanted/src/test/kotlin", "src/queue/src/test/kotlin", "src/activity/src/test/kotlin", "src/calendar/src/test/kotlin", "src/search/src/test/kotlin", "src/settings-mm/src/test/kotlin", "src/settings-quality/src/test/kotlin", "src/settings-connect/src/test/kotlin", "src/settings-system/src/test/kotlin")
            resources.srcDirs("src/library/src/test/resources", "src/detail/src/test/resources", "src/addnew/src/test/resources", "src/wanted/src/test/resources", "src/queue/src/test/resources", "src/activity/src/test/resources", "src/calendar/src/test/resources", "src/search/src/test/resources", "src/settings-mm/src/test/resources", "src/settings-quality/src/test/resources", "src/settings-connect/src/test/resources", "src/settings-system/src/test/resources")
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
    implementation(project(":arr-net"))
    implementation(project(":arr-data"))
    implementation(project(":arr-ui-core"))
    implementation(project(":arr-api-radarr"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.tooling.preview)
}
