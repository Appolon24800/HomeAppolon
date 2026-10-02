import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localSigningProperties = Properties().apply {
    val propertiesFile = rootProject.file("keystore/signing.properties")
    if (propertiesFile.isFile) {
        propertiesFile.inputStream().use { load(it) }
    }
}

fun signingValue(name: String): String? =
    providers.environmentVariable(name).orNull ?: localSigningProperties.getProperty(name)

android {
    namespace = "dev.appolon.homelab"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.appolon.homelab"
        minSdk = 29
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = providers.gradleProperty("appVersionCode").orElse("1").get().toInt()
        versionName = providers.gradleProperty("appVersionName").orElse("1.0").get()
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(signingValue("ANDROID_KEYSTORE_FILE") ?: "keystore/homelab.jks")
            storePassword = signingValue("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = signingValue("ANDROID_KEY_ALIAS")
            keyPassword = signingValue("ANDROID_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)

    implementation(libs.okhttp)
    implementation(libs.kaml)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.svg)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
