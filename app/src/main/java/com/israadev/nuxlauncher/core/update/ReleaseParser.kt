package com.israadev.nuxlauncher.core.update

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.israadev.nuxlauncher.AppLinks

/** Satu rilis dari daftar GitHub yang nomor versinya valid. */
class ReleaseInfo(
    val tag: String,
    val version: AppVersion,
    val notes: String,
    val pageUrl: String,
    val apks: List<ApkAsset>
)

/** Hasil membandingkan daftar rilis dengan versi yang terpasang. */
sealed interface Evaluation {
    data object UpToDate : Evaluation

    data class Newer(val update: AppUpdate) : Evaluation

    /** Ada rilis lebih baru, tetapi tidak ada APK untuk jenis CPU HP ini (mis. build 32-bit gagal). */
    data class NoApkForDevice(val tag: String, val pageUrl: String) : Evaluation
}

/**
 * Membaca balasan GitHub `GET /repos/{repo}/releases` dan memilih rilis yang pantas ditawarkan.
 * Tanpa ketergantungan Android, jadi bisa diuji di JVM.
 *
 * Aturan:
 *  - draf dan tag yang bukan nomor versi (mis. "build-12") dilewati;
 *  - pra-rilis ikut dihitung (versi beta memang ditandai pra-rilis oleh workflow);
 *  - hanya APK yang berasal dari repo resmi dan sudah selesai diunggah yang dianggap;
 *  - rilis membawa dua APK: "...-32bit.apk" untuk CPU 32-bit dan satunya untuk 64-bit. Yang dipilih
 *    cocok dengan proses aplikasi ini, supaya pembaruan tidak mengganti 64-bit dengan 32-bit atau sebaliknya;
 *  - rilis terbaru yang tidak punya APK cocok tidak menutupi rilis lebih lama yang punya.
 */
object ReleaseParser {
    private const val MAX_NOTES_CHARS = 1200

    /** Mengurai daftar rilis. Melempar exception bila isinya bukan daftar rilis GitHub. */
    fun parse(json: String): List<ReleaseInfo> {
        val root = JsonParser.parseString(json)
        require(root.isJsonArray) { "balasan bukan daftar rilis" }
        val out = ArrayList<ReleaseInfo>()
        for (element in root.asJsonArray) {
            if (!element.isJsonObject) continue
            val release = element.asJsonObject
            if (release.bool("draft")) continue

            val tag = release.str("tag_name") ?: continue
            val version = AppVersion.parse(tag) ?: continue
            val pageUrl = release.str("html_url")?.takeIf { isOfficialUrl(it) } ?: continue
            out += ReleaseInfo(
                tag = tag,
                version = version,
                notes = release.str("body").orEmpty(),
                pageUrl = pageUrl,
                apks = parseAssets(release.get("assets"))
            )
        }
        return out
    }

    fun evaluate(releases: List<ReleaseInfo>, current: AppVersion, want32Bit: Boolean): Evaluation {
        var best: Pair<ReleaseInfo, ApkAsset>? = null
        var newestWithoutApk: ReleaseInfo? = null
        for (release in releases) {
            if (release.version <= current) continue
            val apk = chooseApk(release.apks, want32Bit)
            if (apk != null) {
                val b = best
                if (b == null || release.version > b.first.version) best = release to apk
            } else {
                val n = newestWithoutApk
                if (n == null || release.version > n.version) newestWithoutApk = release
            }
        }
        val b = best
        if (b != null) {
            val (release, apk) = b
            return Evaluation.Newer(
                AppUpdate(
                    tag = release.tag,
                    versionName = release.tag.removePrefix("v"),
                    notes = release.notes,
                    pageUrl = release.pageUrl,
                    apk = apk
                )
            )
        }
        val n = newestWithoutApk
        if (n != null) return Evaluation.NoApkForDevice(n.tag, n.pageUrl)
        return Evaluation.UpToDate
    }

    fun evaluateJson(json: String, current: AppVersion, want32Bit: Boolean): Evaluation =
        evaluate(parse(json), current, want32Bit)

    /** APK "...32bit..." untuk HP 32-bit, APK lainnya untuk 64-bit. */
    fun chooseApk(apks: List<ApkAsset>, want32Bit: Boolean): ApkAsset? =
        apks.firstOrNull { it.name.contains("32bit", ignoreCase = true) == want32Bit }

    /**
     * Catatan rilis dirapikan untuk pop-up: tanpa judul, tautan, dan petunjuk memilih APK (aplikasi
     * memilihnya sendiri); butir "- " jadi "• ".
     */
    fun readableNotes(raw: String): String =
        raw.lineSequence()
            .map { it.trimEnd() }
            .filterNot {
                it.startsWith("#") ||
                    it.contains("https://") ||
                    it.startsWith("Pilih APK", ignoreCase = true)
            }
            .map { line ->
                val plain = line.replace("**", "")
                if (plain.startsWith("- ")) "• " + plain.drop(2) else plain
            }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
            .take(MAX_NOTES_CHARS)

    private fun parseAssets(element: JsonElement?): List<ApkAsset> {
        if (element == null || !element.isJsonArray) return emptyList()
        val out = ArrayList<ApkAsset>()
        for (asset in element.asJsonArray) {
            if (!asset.isJsonObject) continue
            val o = asset.asJsonObject
            val name = o.str("name") ?: continue
            val url = o.str("browser_download_url") ?: continue
            if (!name.endsWith(".apk", ignoreCase = true) || !isOfficialUrl(url)) continue
            // Berkas yang masih diunggah berstatus "open"; hanya "uploaded" yang utuh.
            val state = o.str("state")
            if (state != null && state != "uploaded") continue
            out += ApkAsset(
                name = name,
                url = url,
                sizeBytes = o.long("size")?.takeIf { it > 0 },
                sha256 = o.str("digest")?.let { parseSha256(it) }
            )
        }
        return out
    }

    /** "sha256:ABCD..." menjadi 64 huruf hex kecil; null bila bentuknya lain. */
    internal fun parseSha256(digest: String): String? {
        val value = digest.trim().removePrefix("sha256:").lowercase()
        return value.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
    }

    // Hanya tautan ke repo resmi yang dipakai, apa pun isi balasan server.
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

    private fun JsonObject.long(name: String): Long? {
        val value = get(name) ?: return null
        return if (value.isJsonPrimitive && value.asJsonPrimitive.isNumber) value.asLong else null
    }
}
