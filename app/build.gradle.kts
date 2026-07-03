import com.android.build.api.artifact.ArtifactTransformationRequest
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.BuiltArtifact
import com.android.build.api.variant.FilterConfiguration
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.nio.file.Files
import java.util.Properties
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    // AGP 9 provides built-in Kotlin (registers the `kotlin` extension), so the
    // standalone kotlin.android plugin is NOT applied. KSP is incompatible with
    // built-in Kotlin, so Room/Hilt run through KAPT via com.android.legacy-kapt.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.legacy.kapt)
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
        sourceCompatibility = JavaVersion.VERSION_24
        targetCompatibility = JavaVersion.VERSION_24
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

}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24)
    }
}

// AGP 9 removed the legacy applicationVariants output API. Reproduce the per-ABI
// APK naming ("nzbconnect[-debug]-<abi>-<versionName>.apk") through the new
// androidComponents artifact-transform API.
androidComponents {
    onVariants(selector().all()) { variant ->
        val renameProvider =
            tasks.register<RenameApksTask>("rename${variant.name.replaceFirstChar { it.uppercase() }}Apks")
        val request = variant.artifacts.use(renameProvider)
            .wiredWithDirectories(RenameApksTask::apkFolder, RenameApksTask::outFolder)
            .toTransformMany(SingleArtifact.APK)
        renameProvider.configure {
            transformationRequest.set(request)
            appName.set("nzbconnect")
            buildTypeName.set(variant.buildType ?: "")
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
    kapt(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

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

interface CopyApkParameters : WorkParameters {
    val inputApkFile: RegularFileProperty
    val outputApkFile: RegularFileProperty
}

abstract class CopyApkWorkAction : WorkAction<CopyApkParameters> {
    override fun execute() {
        val output = parameters.outputApkFile.get().asFile
        output.delete()
        Files.copy(parameters.inputApkFile.get().asFile.toPath(), output.toPath())
    }
}

abstract class RenameApksTask @Inject constructor(
    private val workerExecutor: WorkerExecutor,
) : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val apkFolder: DirectoryProperty

    @get:OutputDirectory
    abstract val outFolder: DirectoryProperty

    @get:Internal
    abstract val transformationRequest: Property<ArtifactTransformationRequest<RenameApksTask>>

    @get:Input
    abstract val appName: Property<String>

    @get:Input
    abstract val buildTypeName: Property<String>

    @TaskAction
    fun taskAction() {
        // Filenames embed the versionName, so a new build creates new files instead of
        // overwriting old ones; clear stale APKs first so they don't accumulate.
        outFolder.get().asFile.listFiles()?.forEach { f ->
            if (f.name.endsWith(".apk")) {
                f.delete()
            }
        }

        transformationRequest.get().submit(
            this,
            workerExecutor.noIsolation(),
            CopyApkWorkAction::class.java,
        ) { builtArtifact: BuiltArtifact, outputLocation: Directory, params: CopyApkParameters ->
            val abi = builtArtifact.filters.find {
                it.filterType == FilterConfiguration.FilterType.ABI
            }?.identifier

            // Join only the non-null segments so the ABI part drops out cleanly
            // when ABI splits aren't enabled.
            val parts = if (buildTypeName.get() == "debug") {
                listOfNotNull(appName.get(), "debug", abi, builtArtifact.versionName)
            } else {
                listOfNotNull(appName.get(), abi, builtArtifact.versionName)
            }
            val newName = parts.joinToString("-") + ".apk"

            params.inputApkFile.set(File(builtArtifact.outputFile))
            params.outputApkFile.set(File(outputLocation.asFile, newName))
            params.outputApkFile.get().asFile
        }
    }
}
