import java.util.Properties
import java.io.File
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { stream ->
        localProperties.load(stream)
    }
}

// Kunci tanda tangan rilis (pribadi, tidak disimpan di repo). GitHub Actions memasangnya dari Secrets:
//   AMBER_KEYSTORE_FILE     = jalur berkas keystore PKCS12 (alias "amber")
//   AMBER_KEYSTORE_PASSWORD = kata sandi keystore (sekaligus kata sandi kunci)
val releaseKeystoreFile: File? = System.getenv("AMBER_KEYSTORE_FILE")
    ?.takeIf { it.isNotBlank() }
    ?.let { File(it) }
    ?.takeIf { it.isFile }
val releaseKeystorePassword: String? = System.getenv("AMBER_KEYSTORE_PASSWORD")
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
val hasReleaseKey = releaseKeystoreFile != null && releaseKeystorePassword != null

// Di repo resmi, APK tidak boleh dibuat tanpa kunci rilis. Tanpa pagar ini, APK bertanda tangan kunci
// debug yang tidak rahasia bisa ikut terbit dan dipalsukan orang lain. Fork dan build lokal tetap
// bisa jalan dengan kunci debug.
val isOfficialRepoBuild = System.getenv("GITHUB_REPOSITORY").equals("ItsVynzzzz/AmberLauncher", ignoreCase = true)
if (isOfficialRepoBuild && !hasReleaseKey) {
    throw GradleException(
        "Kunci tanda tangan rilis belum terpasang. Isi Secrets AMBER_KEYSTORE_BASE64 dan " +
            "AMBER_KEYSTORE_PASSWORD di Settings > Secrets and variables > Actions, " +
            "lalu pastikan berkas workflow terbaru (build-apk.yml) sudah diunggah."
    )
}

android {
    namespace = "com.israadev.nuxlauncher"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.itsvynzzzz.amberlauncher"
        minSdk = 26
        targetSdk = 34
        // Nomor versi naik sendiri di setiap build GitHub Actions (nomor run), jadi Android selalu
        // menganggapnya pembaruan yang lebih baru dari yang sudah terpasang. Build lokal tetap 10.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { 100 + it } ?: 10
        versionName = "1.0.9"


        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters.clear()
            abiFilters.add("arm64-v8a")
        }
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("amberRelease") {
                storeFile = releaseKeystoreFile
                storeType = "pkcs12"
                storePassword = releaseKeystorePassword
                keyAlias = "amber"
                keyPassword = releaseKeystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("amberRelease")
            } else {
                // Hanya untuk fork dan build lokal (kunci debug bawaan Android)
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.commons.compress)
    implementation(libs.xz)
    implementation(libs.bytehook)
    implementation("androidx.webkit:webkit:1.12.1")

    debugImplementation(libs.androidx.ui.tooling)
}
