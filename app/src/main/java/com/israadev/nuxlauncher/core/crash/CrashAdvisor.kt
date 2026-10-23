package com.israadev.nuxlauncher.core.crash

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.israadev.nuxlauncher.AppLinks
import com.israadev.nuxlauncher.ui.i18n.Tr
import kotlin.math.roundToInt

/** Dugaan awal tentang asal sebuah crash. Hanya petunjuk; penyebab pastinya tetap ada di log. */
enum class CrashSource { LAUNCHER, MEMORY, GRAPHICS, GAME_OR_MODS, UNKNOWN }

/**
 * Penjelasan crash untuk pemain awam: menebak asal crash dari kode keluar dan isi log, lalu menyusun
 * teks siap tempel untuk AI (Claude, ChatGPT, Gemini) supaya pemain bisa mencari solusinya sendiri
 * sebelum melapor ke pengembang. Teks yang disalin mengikuti bahasa tampilan launcher, jadi AI
 * menjawab dalam bahasa yang sama.
 */
object CrashAdvisor {

    const val URL_CLAUDE = "https://claude.ai/new"
    const val URL_CHATGPT = "https://chatgpt.com/"
    const val URL_GEMINI = "https://gemini.google.com/app"

    /** Halaman laporan di GitHub (repo diambil dari AppLinks). */
    val reportUrl: String
        get() = "https://github.com/${AppLinks.GITHUB_REPO}/issues/new"

    /** Batas panjang log yang disalin: awal log (pesan galat) dan akhir log (kejadian terakhir). */
    private const val MAX_LOG_CHARS = 14_000
    private const val LOG_HEAD_CHARS = 4_000
    private const val MAX_MODS = 40

    private const val PROMPT_INTRO_GAME =
        "Halo, Minecraft Java Edition saya crash di HP Android saat dijalankan lewat AmberLauncher. " +
            "Tolong jelaskan penyebabnya dengan bahasa sederhana (saya masih pemula), lalu beri langkah " +
            "perbaikan satu per satu. Kalau penyebabnya mod, sebutkan mod mana yang perlu dihapus atau diganti."

    private const val PROMPT_INTRO_LAUNCHER =
        "Halo, aplikasi AmberLauncher (launcher Minecraft Java Edition untuk Android) berhenti sendiri. " +
            "Tolong jelaskan penyebabnya dengan bahasa sederhana (saya masih pemula) dan beri saran yang " +
            "bisa saya coba. Kalau menurutmu ini bug di launcher, tolong katakan."

    /** Menebak dari mana crash berasal. Hanya dugaan: kode keluar dan kata kunci di log. */
    fun sourceOf(info: GameCrashInfo): CrashSource {
        if (info.isLauncherCrash) return CrashSource.LAUNCHER
        val code = info.exitCode
        if (code == 137 || (info.isSignal && code == 9) || info.logSnippet.contains("OutOfMemoryError")) {
            return CrashSource.MEMORY
        }
        if (code == 134 || code == 139 || (info.isSignal && (code == 4 || code == 6 || code == 7 || code == 8 || code == 11))) {
            return CrashSource.GRAPHICS
        }
        if (!info.isSignal && code == 1) return CrashSource.GAME_OR_MODS
        return CrashSource.UNKNOWN
    }

    /** Uraian singkat status keluar (diterjemahkan lewat kamus oleh pemanggil). */
    fun describeExit(info: GameCrashInfo): String {
        val code = info.exitCode
        return when {
            code == 137 || code == 139 || code == 134 || code == 130 -> CrashUtils.getExitCodeDescription(code)
            info.isSignal -> CrashUtils.getSignalName(code)
            else -> CrashUtils.getExitCodeDescription(code)
        }
    }

    /** Log yang aman dibagikan: token dan kata sandi disamarkan, panjangnya dibatasi. */
    fun logForSharing(logText: String): String = trimLog(LogRedactor.redact(logText))

    /** Teks siap tempel untuk AI: pertanyaan, data perangkat dan game, lalu log crash. */
    fun buildAiPrompt(context: Context, info: GameCrashInfo, logText: String): String {
        val sb = StringBuilder()
        sb.append(Tr.t(if (info.isLauncherCrash) PROMPT_INTRO_LAUNCHER else PROMPT_INTRO_GAME))
        sb.append("\n\n")
        sb.append(Tr.t("Perangkat: ${deviceLine(context)}")).append('\n')
        if (!info.isLauncherCrash) {
            sb.append(Tr.t("Versi Minecraft: ${info.mcVersion}")).append('\n')
            val loader = if (info.loader.equals("vanilla", ignoreCase = true)) {
                "vanilla"
            } else {
                "${info.loader} ${info.loaderVersion ?: ""}".trim()
            }
            sb.append("Loader: ").append(loader).append('\n')
            if (info.installedMods.isNotEmpty()) {
                sb.append(Tr.t("Jumlah mod: ${info.installedMods.size}")).append('\n')
                sb.append(Tr.t("Daftar mod: ${modList(info.installedMods)}")).append('\n')
            }
            sb.append(Tr.t("Status keluar: ${describeExit(info)}")).append('\n')
        }
        sb.append('\n')
        sb.append(Tr.t("Isi log crash:"))
        sb.append("\n```\n")
        sb.append(logForSharing(logText))
        sb.append("\n```\n")
        return sb.toString()
    }

    private fun modList(mods: List<String>): String {
        val shown = mods.take(MAX_MODS).joinToString(", ")
        return if (mods.size > MAX_MODS) "$shown (+${mods.size - MAX_MODS})" else shown
    }

    private fun deviceLine(context: Context): String {
        val ramGb = try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            (mi.totalMem / 1073741824.0).roundToInt()
        } catch (_: Throwable) {
            0
        }
        val base = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}"
        return if (ramGb > 0) "$base, RAM $ramGb GB" else base
    }

    private fun trimLog(text: String): String {
        if (text.length <= MAX_LOG_CHARS) return text
        val head = text.substring(0, LOG_HEAD_CHARS)
        val tail = text.substring(text.length - (MAX_LOG_CHARS - LOG_HEAD_CHARS))
        return head + "\n\n[...]\n\n" + tail
    }
}
