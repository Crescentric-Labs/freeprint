plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "crescentric.freeprint.otg"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "crescentric.freeprint.otg"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "2.0.1"

        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    // No Google-signed dependency blob inside the APK: F-Droid rejects it and it would stop the build being reproducible.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

// The About screen shows the real licence files and the changelog. They are copied into the app's assets at build time, so there
// is no second copy to keep in step with the LICENSE and CHANGELOG.md at the repository root or the one shipped with libusb.
// The folder is registered as a generated asset source of every variant, so merging, linting and packaging all wait for it.
abstract class AppAssetsTask : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val license: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val libusbLicense: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val changelog: RegularFileProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        val licenses = File(out, "licenses").apply { mkdirs() }
        license.get().asFile.copyTo(File(licenses, "GPL-3.0.txt"), overwrite = true)
        libusbLicense.get().asFile.copyTo(File(licenses, "libusb-LGPL-2.1.txt"), overwrite = true)
        changelog.get().asFile.copyTo(File(out, "CHANGELOG.md"), overwrite = true)
    }
}

val appAssets = tasks.register<AppAssetsTask>("appAssets") {
    license.set(rootProject.layout.projectDirectory.file("LICENSE"))
    libusbLicense.set(layout.projectDirectory.file("src/main/cpp/third_party/libusb/COPYING"))
    changelog.set(rootProject.layout.projectDirectory.file("CHANGELOG.md"))
    outputDir.set(layout.buildDirectory.dir("generated/appAssets"))
}

androidComponents {
    onVariants { variant -> variant.sources.assets?.addGeneratedSourceDirectory(appAssets, AppAssetsTask::outputDir) }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
