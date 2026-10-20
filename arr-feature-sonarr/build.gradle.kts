plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.appolon.homelab.arr.feature.sonarr"
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
            kotlin.srcDirs("src/main/kotlin", "src/library/kotlin", "src/library/main/kotlin", "src/detail/kotlin", "src/detail/main/kotlin", "src/addnew/kotlin", "src/addnew/main/kotlin", "src/wanted/kotlin", "src/wanted/main/kotlin", "src/queue/kotlin", "src/queue/main/kotlin", "src/activity/kotlin", "src/activity/main/kotlin", "src/calendar/kotlin", "src/calendar/main/kotlin", "src/search/kotlin", "src/search/main/kotlin", "src/settings-mm/kotlin", "src/settings-mm/main/kotlin", "src/settings-quality/kotlin", "src/settings-quality/main/kotlin", "src/settings-connect/kotlin", "src/settings-connect/main/kotlin", "src/settings-system/kotlin", "src/settings-system/main/kotlin")
            res.srcDirs("src/library/res", "src/library/main/res", "src/detail/res", "src/detail/main/res", "src/addnew/res", "src/addnew/main/res", "src/wanted/res", "src/wanted/main/res", "src/queue/res", "src/queue/main/res", "src/activity/res", "src/activity/main/res", "src/calendar/res", "src/calendar/main/res", "src/search/res", "src/search/main/res", "src/settings-mm/res", "src/settings-mm/main/res", "src/settings-quality/res", "src/settings-quality/main/res", "src/settings-connect/res", "src/settings-connect/main/res", "src/settings-system/res", "src/settings-system/main/res")
        }
        getByName("test") {
            kotlin.srcDirs("src/library/src/test/kotlin", "src/library/test/kotlin", "src/detail/src/test/kotlin", "src/detail/test/kotlin", "src/addnew/src/test/kotlin", "src/addnew/test/kotlin", "src/wanted/src/test/kotlin", "src/wanted/test/kotlin", "src/queue/src/test/kotlin", "src/queue/test/kotlin", "src/activity/src/test/kotlin", "src/activity/test/kotlin", "src/calendar/src/test/kotlin", "src/calendar/test/kotlin", "src/search/src/test/kotlin", "src/search/test/kotlin", "src/settings-mm/src/test/kotlin", "src/settings-mm/test/kotlin", "src/settings-quality/src/test/kotlin", "src/settings-quality/test/kotlin", "src/settings-connect/src/test/kotlin", "src/settings-connect/test/kotlin", "src/settings-system/src/test/kotlin", "src/settings-system/test/kotlin")
            resources.srcDirs("src/library/src/test/resources", "src/library/test/resources", "src/detail/src/test/resources", "src/detail/test/resources", "src/addnew/src/test/resources", "src/addnew/test/resources", "src/wanted/src/test/resources", "src/wanted/test/resources", "src/queue/src/test/resources", "src/queue/test/resources", "src/activity/src/test/resources", "src/activity/test/resources", "src/calendar/src/test/resources", "src/calendar/test/resources", "src/search/src/test/resources", "src/search/test/resources", "src/settings-mm/src/test/resources", "src/settings-mm/test/resources", "src/settings-quality/src/test/resources", "src/settings-quality/test/resources", "src/settings-connect/src/test/resources", "src/settings-connect/test/resources", "src/settings-system/src/test/resources", "src/settings-system/test/resources")
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
    implementation(project(":arr-api-sonarr"))
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
