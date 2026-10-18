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
}

// The About screen shows the real licence files. They are copied into the app's assets at build time, so there is no second
// copy to keep in step with the LICENSE at the repository root or with the one shipped with libusb.
val copyLicenses by tasks.registering(Copy::class) {
    from(rootProject.file("LICENSE")) { rename { "GPL-3.0.txt" } }
    from(file("src/main/cpp/third_party/libusb/COPYING")) { rename { "libusb-LGPL-2.1.txt" } }
    into(layout.buildDirectory.dir("generated/licenseAssets/licenses"))
}
android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/licenseAssets"))
tasks.configureEach { if (name.startsWith("merge") && name.endsWith("Assets")) dependsOn(copyLicenses) }

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
