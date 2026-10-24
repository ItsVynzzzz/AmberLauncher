import java.util.Properties
import java.io.File
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Dasar nama versi dari berkas VERSION di akar repo. Ubah isinya (mis. 0.2.0-beta atau 1.0.0) untuk
// naik ke seri versi berikutnya; nomor build di belakangnya otomatis mulai dari 1 lagi.
fun baseVersionName(): String =
    rootProject.file("VERSION").takeIf { it.isFile }
        ?.readText()?.trim()?.takeIf { it.isNotEmpty() }
        ?: "0.1.0-beta"

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

// APK 32-bit (armeabi-v7a) dibangun terpisah oleh scripts/build-arm32.sh dengan AMBER_ABI=arm32.
// Tanpa variabel itu (build biasa dan APK 64-bit) semua pengaturan di bawah sama persis seperti
// sebelumnya: hanya arm64-v8a, tanpa build native.
val buildArm32 = System.getenv("AMBER_ABI") == "arm32"

android {
    namespace = "com.israadev.nuxlauncher"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.itsvynzzzz.amberlauncher"
        minSdk = 26
        targetSdk = 34
        // versionCode (dipakai Android untuk menentukan pembaruan) naik sendiri di setiap build GitHub
        // Actions lewat nomor run, jadi JANGAN direset walau nama versi dimulai ulang: Android menolak
        // memasang APK dengan versionCode lebih kecil dari yang sudah terpasang. Build lokal tetap 10.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { 100 + it } ?: 10
        // Nama versi yang dilihat pengguna: dasarnya dari berkas VERSION di akar repo (mis. 0.1.0-beta).
        // GitHub Actions menambahkan nomor build di belakangnya lewat AMBER_VERSION_NAME (0.1.0-beta.7);
        // build lokal diberi akhiran ".dev" supaya tidak pernah dianggap pembaruan.
        versionName = System.getenv("AMBER_VERSION_NAME")?.trim()?.takeIf { it.isNotEmpty() }
            ?: "${baseVersionName()}.dev"


        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters.clear()
            abiFilters.add(if (buildArm32) "armeabi-v7a" else "arm64-v8a")
        }
    }

    if (buildArm32) {
        // Pustaka jembatan (pojavexec, exithook, dst.) untuk 32-bit dibangun dari kode C yang
        // disalin ke src/main/jni oleh scripts/build-arm32.sh. Versi NDK sama dengan proyek hulu.
        ndkVersion = "25.2.9519653"
        externalNativeBuild {
            ndkBuild {
                path = file("src/main/jni/Android.mk")
            }
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
        // Dibutuhkan Android.mk untuk menautkan bytehook; hanya aktif di build 32-bit.
        prefab = buildArm32
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
            if (buildArm32) pickFirsts += listOf("**/libbytehook.so")
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
    // Klien proxy resmi mod TouchController (LGPL-3.0). Dipanggil lewat refleksi, lihat TouchControllerBridge.
    implementation(libs.proxy.client.android)
    implementation("androidx.webkit:webkit:1.12.1")
    // Memasang profil dasar (baseline profile) milik aplikasi dan pustaka AndroidX saat pertama dibuka,
    // termasuk APK yang dipasang di luar Play Store, supaya launcher lebih cepat terbuka di HP lemah.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    debugImplementation(libs.androidx.ui.tooling)
}
