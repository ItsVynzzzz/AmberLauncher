package com.israadev.nuxlauncher.core.vulkan

import com.israadev.nuxlauncher.core.utils.NuxVersionUtils

/** Tingkat kabar dari [McVulkan.verdict]; UI memilih warnanya dari sini. */
enum class VerdictLevel { CHECKING, OK, INFO, WARN, BAD }

/**
 * Kesimpulan satu baris untuk pemain. [text] kalimat tetap berbahasa Indonesia (kuncinya ada di
 * assets/lang/<bahasa>/minecraft26.tsv supaya ikut diterjemahkan); [detail] berisi nilai teknis (versi Vulkan,
 * nama ekstensi) yang sengaja tidak diterjemahkan, boleh kosong.
 */
class Verdict(val level: VerdictLevel, val text: String, val detail: String = "")

/**
 * Syarat Vulkan Minecraft versi bernomor tahun (26.x ke atas) dan penilaiannya terhadap [VulkanReport] HP ini.
 *
 * Minecraft mulai punya backend Vulkan pada 26.2 (sebelumnya OpenGL). Daftar ekstensi dan fitur yang wajib ada
 * mengikuti tabel kebutuhan ZalithLauncher 2 (VulkanRequirements, versi 1) yang disusun dari kode game; dengan
 * dasar Vulkan 1.2. Penilaian ini perkiraan: lolos berarti syarat yang dikenal terpenuhi, bukan jaminan game jalan.
 */
object McVulkan {
    /** Versi Minecraft pertama dengan backend Vulkan. */
    const val FIRST_VULKAN_MC = "26.2"

    private class Need(
        val name: String,
        val isFeature: Boolean,
        val from: String,
        /** Versi terakhir (ikut dihitung) yang membutuhkannya; null = seterusnya. */
        val through: String? = null
    ) {
        fun appliesTo(release: String): Boolean {
            if (NuxVersionUtils.compare(release, from) < 0) return false
            return through == null || NuxVersionUtils.compare(release, through) <= 0
        }
    }

    private val NEEDS = listOf(
        Need("VK_KHR_synchronization2", false, "26.2"),
        Need("VK_EXT_vertex_attribute_divisor", false, "26.2"),
        Need("VK_KHR_swapchain", false, "26.2"),
        Need("VK_KHR_dynamic_rendering", false, "26.2", "26.3"),
        Need("VK_KHR_push_descriptor", false, "26.2", "26.3"),
        Need("multiDrawIndirect", true, "26.2"),
        Need("samplerAnisotropy", true, "26.2"),
        Need("shaderDrawParameters", true, "26.2"),
        Need("timelineSemaphore", true, "26.2"),
        Need("hostQueryReset", true, "26.2"),
        Need("synchronization2", true, "26.2"),
        Need("vertexAttributeInstanceRateDivisor", true, "26.2"),
        Need("dynamicRendering", true, "26.2", "26.3"),
        Need("fillModeNonSolid", true, "26.2", "26.2"),
        Need("drawIndirectFirstInstance", true, "26.3")
    )

    /** True untuk versi bernomor tahun: "26.1", "26.2-snapshot-3", "27.0". Bukan "1.21", "b1.7.3", atau "24w14a". */
    fun isYearVersion(mcVersion: String): Boolean {
        val s = mcVersion.trimStart()
        var number = 0
        var i = 0
        while (i < s.length && i < 4 && s[i] in '0'..'9') {
            number = number * 10 + (s[i] - '0')
            i++
        }
        return i > 0 && number >= 26 && (i == s.length || s[i] == '.' || s[i] == '-')
    }

    /** "26.2-snapshot-3", "26.2-pre-1", "26.2-rc-1" dinilai sebagai "26.2" (versi rilis yang mereka tuju). */
    private fun release(mcVersion: String): String {
        val dash = mcVersion.indexOf('-')
        return (if (dash > 0) mcVersion.substring(0, dash) else mcVersion).trim()
    }

    /** True bila versi ini memakai (atau bisa memakai) backend Vulkan. */
    fun usesVulkan(mcVersion: String): Boolean =
        isYearVersion(mcVersion) && NuxVersionUtils.compare(release(mcVersion), FIRST_VULKAN_MC) >= 0

    /** Syarat Vulkan [mcVersion] yang tidak dipenuhi HP ini (kosong = semua terpenuhi). */
    fun missing(report: VulkanReport, mcVersion: String): List<String> {
        val rel = release(mcVersion)
        val out = ArrayList<String>()
        if (!report.meetsBaseline) out.add("Vulkan 1.2")
        for (need in NEEDS) {
            if (need.appliesTo(rel) && !report.has(need.name, need.isFeature)) out.add(need.name)
        }
        return out
    }

    /**
     * Kesimpulan untuk pemain. [mcVersion] null = belum ada versi dipilih (dinilai untuk 26.2 ke atas).
     * [report] null = pengecekan masih berjalan.
     */
    fun verdict(report: VulkanReport?, mcVersion: String?): Verdict {
        if (report == null) {
            return Verdict(VerdictLevel.CHECKING, "Memeriksa dukungan Vulkan HP ini...")
        }
        val picked = mcVersion?.takeIf { isYearVersion(it) }
        if (picked != null && !usesVulkan(picked)) {
            return Verdict(VerdictLevel.INFO, "Versi ini memakai OpenGL. Vulkan baru dipakai mulai Minecraft 26.2.")
        }
        return when (report.state) {
            VulkanState.UNAVAILABLE -> Verdict(
                VerdictLevel.INFO,
                "Pengecekan Vulkan tidak tersedia di build ini, jadi dukungan Vulkan HP belum bisa dipastikan."
            )
            VulkanState.NO_RESPONSE -> Verdict(
                VerdictLevel.WARN,
                "Pengecekan Vulkan tidak selesai. Driver Vulkan HP ini mungkin bermasalah, jadi Minecraft 26.2 ke atas berisiko gagal bila memakai Vulkan."
            )
            VulkanState.NO_VULKAN -> Verdict(
                VerdictLevel.BAD,
                "HP ini tidak punya driver Vulkan yang bisa dipakai. Minecraft 26.2 ke atas tidak bisa memakai Vulkan di sini; pilih Prefer OpenGL di pengaturan video game."
            )
            VulkanState.OK -> {
                val lacking = missing(report, picked ?: FIRST_VULKAN_MC)
                if (lacking.isEmpty()) {
                    Verdict(VerdictLevel.OK, "Vulkan HP ini memenuhi syarat Minecraft 26.2 ke atas.", "Vulkan ${report.versionText}")
                } else {
                    val shown = lacking.take(MAX_LISTED).joinToString(", ")
                    val more = if (lacking.size > MAX_LISTED) " +${lacking.size - MAX_LISTED}" else ""
                    Verdict(
                        VerdictLevel.BAD,
                        "Vulkan HP ini belum memenuhi syarat Minecraft 26.2 ke atas. Bila game memakai Vulkan, bisa gagal dimulai; pilih Prefer OpenGL di pengaturan video game.",
                        "Vulkan ${report.versionText}. $shown$more"
                    )
                }
            }
        }
    }

    private const val MAX_LISTED = 5
}
