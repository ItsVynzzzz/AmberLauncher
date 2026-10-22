package com.israadev.nuxlauncher.core.update

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import com.israadev.nuxlauncher.AppLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Mengecek rilis terbaru di GitHub Releases ([AppLinks.GITHUB_REPO]) dan membandingkannya dengan
 * versi yang terpasang. Pengecekan memakai ETag (lihat [ReleaseFetcher]) sehingga murah dan boleh
 * dilakukan setiap aplikasi dibuka; daftar rilis terakhir disimpan, jadi pop-up tetap bisa muncul
 * dari salinan itu walau sedang offline. Kebijakan kapan mengecek ada di [UpdateManager].
 */
object UpdateChecker {
    sealed interface Result {
        data class Found(val update: AppUpdate) : Result
        data object UpToDate : Result
        data class NoApk(val tag: String, val pageUrl: String) : Result
        data class Failed(val failure: UpdateFailure) : Result
    }

    private const val PER_PAGE = 10

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    /** APK 32-bit dipilih bila proses aplikasi ini 32-bit (juga di HP 64-bit yang memasang APK 32-bit). */
    fun wants32BitApk(): Boolean = !android.os.Process.is64Bit()

    fun currentVersionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    } catch (_: Exception) {
        ""
    }

    fun currentVersionCode(context: Context): Long = try {
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
    } catch (_: Exception) {
        0L
    }

    /**
     * Nomor versi terpasang. Nama yang bukan semver (build sangat lama, mis. "1.0") dianggap 0.0.0,
     * jadi rilis apa pun dianggap lebih baru.
     */
    private fun currentVersion(name: String): AppVersion =
        AppVersion.parse(name) ?: AppVersion.parse("0.0.0")!!

    /** Menjawab dari daftar rilis tersimpan tanpa internet. Null bila belum pernah ada daftar. Jalankan di thread latar. */
    fun evaluateCached(context: Context): Result? {
        val cache = UpdateStore.readCache(context) ?: return null
        val current = currentVersion(currentVersionName(context))
        return try {
            toResult(ReleaseParser.evaluateJson(cache.body, current, wants32BitApk()))
        } catch (_: Exception) {
            null
        }
    }

    /** Bertanya ke GitHub (dengan ETag) lalu menjawab. Aman dipanggil dari thread mana pun. */
    suspend fun check(context: Context): Result = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val name = currentVersionName(app)
        val current = currentVersion(name)
        // ETag hanya dipakai bila daftar tersimpannya masih terbaca. Kalau tidak, GitHub akan terus
        // menjawab 304 ("tidak berubah") untuk daftar yang sebenarnya tidak bisa dipakai.
        val cache = UpdateStore.readCache(app)?.takeIf { isReadable(it.body) }
        val url = "https://api.github.com/repos/${AppLinks.GITHUB_REPO}/releases?per_page=$PER_PAGE"

        when (val fetched = ReleaseFetcher.fetch(client, url, "AmberLauncher/$name", cache?.etag)) {
            is ReleaseFetcher.Result.Fresh -> {
                val evaluation = try {
                    ReleaseParser.evaluateJson(fetched.body, current, wants32BitApk())
                } catch (_: Exception) {
                    return@withContext Result.Failed(
                        UpdateFailure(FailureKind.BAD_RESPONSE, "Balasan GitHub tidak bisa dibaca.")
                    )
                }
                // Disimpan hanya bila isinya benar-benar daftar rilis, supaya balasan rusak tidak meracuni cache.
                UpdateStore.writeCache(app, fetched.etag, fetched.body)
                UpdateStore.setBlockedUntil(app, 0L)
                toResult(evaluation)
            }
            ReleaseFetcher.Result.NotModified -> {
                UpdateStore.setBlockedUntil(app, 0L)
                evaluateCached(app)
                    ?: Result.Failed(UpdateFailure(FailureKind.BAD_RESPONSE, "Daftar rilis tersimpan tidak bisa dibaca."))
            }
            is ReleaseFetcher.Result.Failure -> {
                if (fetched.failure.kind == FailureKind.RATE_LIMITED) {
                    UpdateStore.setBlockedUntil(app, fetched.failure.retryAtMillis)
                }
                Result.Failed(fetched.failure)
            }
        }
    }

    private fun isReadable(body: String): Boolean = try {
        ReleaseParser.parse(body)
        true
    } catch (_: Exception) {
        false
    }

    /** Pengguna memilih "Lewati": versi ini tidak ditawarkan otomatis lagi, versi yang lebih baru tetap. */
    fun skip(context: Context, tag: String) = UpdateStore.setSkippedTag(context, tag)

    fun isSkipped(context: Context, tag: String): Boolean = UpdateStore.skippedTag(context) == tag

    private fun toResult(e: Evaluation): Result = when (e) {
        is Evaluation.Newer -> Result.Found(e.update)
        Evaluation.UpToDate -> Result.UpToDate
        is Evaluation.NoApkForDevice -> Result.NoApk(e.tag, e.pageUrl)
    }
}
