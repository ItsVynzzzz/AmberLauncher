package com.israadev.nuxlauncher.core.update

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.israadev.nuxlauncher.AppLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Satu rilis baru di GitHub yang bisa ditawarkan ke pengguna. */
data class AppUpdate(
    /** Tag rilis, mis. "v0.1.0-beta.7". */
    val tag: String,
    /** Tag tanpa awalan "v", mis. "0.1.0-beta.7". */
    val versionName: String,
    /** Isi catatan rilis (teks mentah dari GitHub). */
    val notes: String,
    /** Halaman rilis di GitHub. */
    val pageUrl: String,
    /** Alamat langsung berkas APK, bila rilis memilikinya. */
    val downloadUrl: String?
)

/**
 * Mengecek rilis terbaru di GitHub Releases ([AppLinks.GITHUB_REPO]) dan membandingkannya dengan
 * versi yang terpasang. Tanpa login: batas GitHub 60 permintaan per jam per jaringan, jadi
 * pengecekan otomatis dibatasi sekali per 6 jam. Kegagalan jaringan diam-diam diabaikan.
 *
 * Daftar rilis dibaca lewat /releases (bukan /releases/latest) karena /latest tidak menyertakan
 * pra-rilis, sedangkan versi beta ditandai pra-rilis oleh workflow.
 */
object UpdateChecker {
    private const val PREFS_NAME = "amber_launcher_prefs"
    private const val KEY_LAST_CHECK = "update_last_check"
    private const val KEY_SKIPPED_TAG = "update_skipped_tag"
    private const val CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000
    private const val MAX_NOTES_CHARS = 1200

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun currentVersionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    } catch (_: Exception) {
        ""
    }

    /**
     * Mengecek GitHub bila sudah lewat 6 jam dari pengecekan terakhir. Mengembalikan rilis yang lebih
     * baru daripada versi terpasang dan belum dilewati pengguna, atau null (tidak ada, belum
     * waktunya, atau jaringan gagal; kalau gagal, pengecekan diulang saat aplikasi dibuka lagi).
     */
    suspend fun checkIfDue(context: Context): AppUpdate? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_CHECK, 0L) < CHECK_INTERVAL_MS) return@withContext null

        val currentName = currentVersionName(context)
        val current = AppVersion.parse(currentName) ?: return@withContext null

        val result = fetchLatest(currentName)
        if (result.isFailure) return@withContext null
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()

        val latest = result.getOrNull() ?: return@withContext null
        val latestVersion = AppVersion.parse(latest.tag) ?: return@withContext null
        if (latestVersion <= current) return@withContext null
        if (prefs.getString(KEY_SKIPPED_TAG, null) == latest.tag) return@withContext null
        latest
    }

    /** Pengguna memilih "Lewati": versi ini tidak ditawarkan lagi, versi yang lebih baru tetap. */
    fun skip(context: Context, tag: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SKIPPED_TAG, tag).apply()
    }

    /** Catatan rilis dirapikan untuk pop-up: tanpa judul/tautan Markdown, butir "- " jadi "• ". */
    fun readableNotes(raw: String): String =
        raw.lineSequence()
            .map { it.trimEnd() }
            .filterNot { it.startsWith("#") || it.contains("https://") }
            .map { line ->
                val plain = line.replace("**", "")
                if (plain.startsWith("- ")) "• " + plain.drop(2) else plain
            }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
            .take(MAX_NOTES_CHARS)

    /** Rilis dengan nomor versi tertinggi (pra-rilis ikut dihitung). Result gagal = jaringan/respon bermasalah. */
    private fun fetchLatest(userAgentVersion: String): Result<AppUpdate?> = runCatching {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${AppLinks.GITHUB_REPO}/releases?per_page=20")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "AmberLauncher/$userAgentVersion")
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("GitHub membalas ${resp.code}")
            val body = resp.body?.string().orEmpty()

            var best: Pair<AppVersion, AppUpdate>? = null
            for (element in JsonParser.parseString(body).asJsonArray) {
                if (!element.isJsonObject) continue
                val release = element.asJsonObject
                if (release.bool("draft")) continue

                val tag = release.str("tag_name") ?: continue
                val version = AppVersion.parse(tag) ?: continue // tag lama "build-12" dilewati
                val pageUrl = release.str("html_url")?.takeIf { isOfficialUrl(it) } ?: continue

                var downloadUrl: String? = null
                val assets = release.get("assets")
                if (assets != null && assets.isJsonArray) {
                    for (asset in assets.asJsonArray) {
                        if (!asset.isJsonObject) continue
                        val name = asset.asJsonObject.str("name") ?: continue
                        val url = asset.asJsonObject.str("browser_download_url") ?: continue
                        if (name.endsWith(".apk", ignoreCase = true) && isOfficialUrl(url)) {
                            downloadUrl = url
                            break
                        }
                    }
                }

                val update = AppUpdate(
                    tag = tag,
                    versionName = tag.removePrefix("v"),
                    notes = release.str("body").orEmpty(),
                    pageUrl = pageUrl,
                    downloadUrl = downloadUrl
                )
                val currentBest = best
                if (currentBest == null || version > currentBest.first) best = version to update
            }
            best?.second
        }
    }

    // Hanya tautan ke repo resmi yang dibuka, apa pun isi balasan server.
    private fun isOfficialUrl(url: String): Boolean =
        url.startsWith("https://github.com/${AppLinks.GITHUB_REPO}/", ignoreCase = true)

    private fun JsonObject.str(name: String): String? {
        val value = get(name) ?: return null
        return if (value.isJsonPrimitive) value.asString else null
    }

    private fun JsonObject.bool(name: String): Boolean {
        val value = get(name) ?: return false
        return value.isJsonPrimitive && value.asBoolean
    }
}
