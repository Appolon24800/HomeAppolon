plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.appolon.homelab.arr.sync"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }
    testOptions.unitTests.isReturnDefaultValues = true
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
    implementation(libs.androidx.work.runtime)
    implementation(libs.signalr)
    implementation(libs.slf4j.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
