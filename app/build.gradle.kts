import com.android.build.OutputFile
import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.android.git.version)
}

android {
    namespace = "org.cygnusx1.nzbconnect"
    compileSdk = 35

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val hasKeystore = keystorePropertiesFile.exists()

    if (hasKeystore) {
        val keystoreProperties = Properties()
        keystoreProperties.load(keystorePropertiesFile.inputStream())

        signingConfigs {
            create("release") {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    } else {
        println("Warning: keystore.properties file not found. Skipping signing configuration.")
    }

    defaultConfig {
        applicationId = "org.cygnusx1.nzbconnect"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = androidGitVersion.name()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            if (hasKeystore) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                println("Warning: keystore.properties file not found. Skipping signing configuration for release.")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        compose = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false // Set to true if you also want a universal APK
        }
    }

    applicationVariants.all {
        val variant = this
        outputs.all {
            val output = this as BaseVariantOutputImpl
            val appName = "nzbconnect"
            val baseAbiVersion = output.getFilter(OutputFile.ABI)
            val buildType = variant.buildType.name
            val versionName = variant.versionName

            // Join only the non-null segments so the ABI part drops out cleanly
            // when ABI splits aren't enabled.
            val artifactName = if (buildType == "debug") {
                listOfNotNull(appName, buildType, baseAbiVersion, versionName)
            } else {
                listOfNotNull(appName, baseAbiVersion, versionName)
            }.joinToString("-")

            output.outputFileName = "$artifactName.apk"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.security.crypto)
    implementation(libs.coil.compose)
    implementation(libs.reorderable)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
}
