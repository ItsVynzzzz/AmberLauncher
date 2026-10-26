import java.util.Properties
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

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

// ---- Pemangkasan simbol pustaka native (jaring pengaman ukuran) ----
// Beberapa pustaka bawaan repo (libspirv-cross-c-shared, libshaderconv, libc++_shared) masih membawa
// tabel simbol dan info debug: sekitar 47 MB bila terpasang di HP. AGP membuangnya di langkah
// stripReleaseDebugSymbols, tetapi hanya bila NDK ditemukan; tanpa NDK ia diam-diam mengemas apa adanya.
// Setelah langkah itu, pustaka yang masih berisi .symtab dipangkas dengan llvm-strip (cara yang sama
// seperti AGP). Pustaka yang sudah bersih tidak disentuh. Langkah ini tidak pernah menggagalkan build.
fun findLlvmStrip(): File? {
    val exe = if (System.getProperty("os.name").lowercase().contains("win")) "llvm-strip.exe" else "llvm-strip"
    val roots = mutableListOf<File>()
    for (key in listOf("ANDROID_NDK_HOME", "ANDROID_NDK_ROOT", "ANDROID_NDK_LATEST_HOME")) {
        System.getenv(key)?.takeIf { it.isNotBlank() }?.let { roots.add(File(it)) }
    }
    for (key in listOf("ANDROID_HOME", "ANDROID_SDK_ROOT")) {
        System.getenv(key)?.takeIf { it.isNotBlank() }?.let { sdk ->
            File(sdk, "ndk").listFiles()?.sortedByDescending { it.name }?.let { roots.addAll(it) }
        }
    }
    for (root in roots) {
        val hosts = File(root, "toolchains/llvm/prebuilt").listFiles() ?: continue
        for (host in hosts) {
            val tool = File(host, "bin/$exe")
            if (tool.isFile) return tool
        }
    }
    for (dir in (System.getenv("PATH") ?: "").split(File.pathSeparator)) {
        if (dir.isBlank()) continue
        val tool = File(dir, exe)
        if (tool.isFile && tool.canExecute()) return tool
    }
    return null
}

// True bila berkas ELF 64-bit little-endian punya bagian tabel simbol (.symtab, tipe SHT_SYMTAB = 2).
fun elfHasSymbols(file: File): Boolean {
    try {
        RandomAccessFile(file, "r").use { raf ->
            if (raf.length() < 64) return false
            val header = ByteArray(64)
            raf.readFully(header)
            val isElf = header[0].toInt() == 0x7F && header[1].toInt() == 'E'.code &&
                header[2].toInt() == 'L'.code && header[3].toInt() == 'F'.code
            if (!isElf || header[4].toInt() != 2 || header[5].toInt() != 1) return false
            val head = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val shoff = head.getLong(0x28)
            val shentsize = head.getShort(0x3A).toInt() and 0xFFFF
            val shnum = head.getShort(0x3C).toInt() and 0xFFFF
            if (shoff <= 0L || shentsize < 64 || shnum == 0) return false
            if (shoff + shentsize.toLong() * shnum > raf.length()) return false
            val entry = ByteArray(8)
            for (i in 0 until shnum) {
                raf.seek(shoff + i.toLong() * shentsize)
                raf.readFully(entry)
                // Bidang sh_name (4 byte) lalu sh_type (4 byte)
                if (ByteBuffer.wrap(entry).order(ByteOrder.LITTLE_ENDIAN).getInt(4) == 2) return true
            }
        }
    } catch (_: Throwable) {
    }
    return false
}

tasks.configureEach {
    if (name == "stripReleaseDebugSymbols") {
        doLast {
            try {
                val unstripped = outputs.files.files
                    .flatMap { root -> if (root.isDirectory) root.walkTopDown().toList() else listOf(root) }
                    .filter { it.isFile && it.name.endsWith(".so") && elfHasSymbols(it) }
                if (unstripped.isNotEmpty()) {
                    val tool = findLlvmStrip()
                    if (tool == null) {
                        logger.warn("[Amber] ${unstripped.size} pustaka native masih berisi simbol, tetapi llvm-strip tidak ditemukan (NDK belum terpasang).")
                    } else {
                        var saved = 0L
                        var done = 0
                        for (lib in unstripped) {
                            val before = lib.length()
                            val proc = ProcessBuilder(tool.absolutePath, "--strip-unneeded", lib.absolutePath)
                                .redirectErrorStream(true)
                                .start()
                            proc.inputStream.readBytes()
                            if (proc.waitFor() == 0) {
                                saved += before - lib.length()
                                done++
                            }
                        }
                        logger.lifecycle("[Amber] Simbol dipangkas dari $done dari ${unstripped.size} pustaka native, hemat ${saved / 1024 / 1024} MB.")
                    }
                }
            } catch (e: Throwable) {
                logger.warn("[Amber] Pemangkasan simbol dilewati: ${e.message}")
            }
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
