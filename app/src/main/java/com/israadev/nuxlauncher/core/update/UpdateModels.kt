package com.israadev.nuxlauncher.core.update

import java.io.File

/** Jenis kegagalan; UI memakainya untuk memilih tombol yang ditawarkan (mis. "Coba lagi"). */
enum class FailureKind {
    OFFLINE,
    TIMEOUT,
    RATE_LIMITED,
    SERVER,
    BAD_RESPONSE,
    STORAGE_FULL,
    DOWNLOAD,
    CORRUPT_FILE,
    WRONG_APP,
    WRONG_SIGNATURE,
    NOT_NEWER,
    GAME_RUNNING,
    INSTALL_BLOCKED,
    INSTALL_FAILED
}

/**
 * Satu kegagalan beserta kalimat untuk pengguna. Kalimatnya berbahasa Indonesia seperti teks lain di
 * aplikasi dan diterjemahkan saat digambar (lihat assets/lang/<kode>/update.tsv).
 */
data class UpdateFailure(
    val kind: FailureKind,
    val message: String,
    /** Hanya untuk RATE_LIMITED: kapan boleh mencoba lagi (epoch ms). Selain itu 0. */
    val retryAtMillis: Long = 0L
)

/** Satu berkas APK di sebuah rilis GitHub. */
data class ApkAsset(
    val name: String,
    val url: String,
    /** Ukuran menurut GitHub (byte); null bila tidak dilaporkan. */
    val sizeBytes: Long?,
    /** SHA-256 (huruf kecil) menurut GitHub; null untuk berkas lama yang belum punya digest. */
    val sha256: String?
)

/** Rilis lebih baru yang bisa ditawarkan, lengkap dengan APK yang cocok untuk HP ini (32/64-bit). */
data class AppUpdate(
    /** Tag rilis, mis. "v0.1.0-beta.7". */
    val tag: String,
    /** Tag tanpa awalan "v", mis. "0.1.0-beta.7". */
    val versionName: String,
    /** Isi catatan rilis (teks mentah dari GitHub). */
    val notes: String,
    /** Halaman rilis di GitHub. */
    val pageUrl: String,
    val apk: ApkAsset
)

/** Keadaan pembaruan yang dilihat UI (pop-up dan tab Pembaruan di Pengaturan). */
sealed interface UpdateState {
    /** Belum ada yang dicek pada sesi ini. */
    data object Idle : UpdateState

    /** Pengecekan manual ke GitHub sedang berjalan. */
    data object Checking : UpdateState

    data class UpToDate(val checkedAt: Long) : UpdateState

    /** Ada rilis lebih baru, tetapi belum punya APK untuk jenis CPU HP ini. */
    data class NoApk(val tag: String, val pageUrl: String) : UpdateState

    /** Pengecekan atau tindakan tanpa konteks rilis gagal. Hanya tampil di Pengaturan. */
    data class Failed(val failure: UpdateFailure) : UpdateState

    data class Available(val update: AppUpdate) : UpdateState

    /** [totalBytes] 0 berarti ukurannya belum diketahui. */
    data class Downloading(val update: AppUpdate, val doneBytes: Long, val totalBytes: Long) : UpdateState

    data class ReadyToInstall(val update: AppUpdate, val apk: File, val note: String? = null) : UpdateState

    data class NeedsInstallPermission(val update: AppUpdate, val apk: File) : UpdateState

    data class Installing(val update: AppUpdate) : UpdateState

    /** Unduh atau pasang gagal. [apk] terisi bila berkasnya sudah utuh (tinggal dipasang ulang). */
    data class ActionFailed(
        val update: AppUpdate,
        val failure: UpdateFailure,
        val apk: File? = null
    ) : UpdateState
}
