package com.israadev.nuxlauncher.core.instance

/**
 * Aturan nama folder instance di `instances/`.
 *
 * Dulu folder bernama id acak ("inst_3f9c..."), sulit dicari di pengelola berkas. Sekarang foldernya
 * mengikuti nama instance. Id tetap dipakai di dalam launcher (tidak pernah berubah); nama folder
 * disimpan di Instance.dirName.
 *
 * Nama folder sengaja dibuat konservatif supaya aman di semua penyimpanan (termasuk kartu SD berformat
 * FAT) dan di jalur yang dipakai game, JVM, dan mod: huruf (semua bahasa), angka, "-", "." dan "_".
 * Karakter lain, termasuk spasi, diganti "_". Fungsi di sini murni (tanpa Android) supaya mudah diuji.
 */
internal object InstanceFolders {
    const val FALLBACK = "Instance"
    private const val MAX_CHARS = 40

    /** Mengubah nama instance menjadi nama folder yang aman, mis. "Survival Seru (1.20.1)" -> "Survival_Seru_1.20.1". */
    fun sanitize(name: String): String {
        val src = name.trim()
        val sb = StringBuilder()
        var chars = 0
        var lastUnderscore = false
        var i = 0
        while (i < src.length && chars < MAX_CHARS) {
            val cp = src.codePointAt(i)
            i += Character.charCount(cp)
            if (isAllowed(cp)) {
                sb.appendCodePoint(cp)
                lastUnderscore = false
                chars++
            } else if (!lastUnderscore) {
                sb.append('_')
                lastUnderscore = true
                chars++
            }
        }
        val cleaned = sb.toString().trim { it == '_' || it == '.' || it == '-' }
        return cleaned.ifEmpty { FALLBACK }
    }

    private fun isAllowed(cp: Int): Boolean {
        if (Character.isLetterOrDigit(cp)) return true
        if (cp == '-'.code || cp == '.'.code) return true
        // Tanda baca yang menempel pada huruf (mis. vokal Thai dan tanda Vietnam) bukan huruf menurut Java
        val type = Character.getType(cp)
        return type == Character.NON_SPACING_MARK.toInt() || type == Character.COMBINING_SPACING_MARK.toInt()
    }

    /** Nama yang boleh dipakai sebagai satu folder: tidak kosong, tanpa pemisah jalur, bukan "." atau "..". */
    fun isSafe(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        if (name == "." || name == "..") return false
        return name.none { it == '/' || it == '\\' || it == '\u0000' }
    }

    /** [base] bila belum dipakai, selain itu [base]_2, [base]_3, dst. Perbandingan tidak membedakan huruf besar/kecil. */
    fun unique(base: String, takenLower: Set<String>): String {
        if (base.lowercase() !in takenLower) return base
        var n = 2
        while (true) {
            val candidate = "${base}_$n"
            if (candidate.lowercase() !in takenLower) return candidate
            n++
        }
    }

    /**
     * Nama folder baru untuk instance bernama [name] yang sekarang berfolder [current], atau null bila
     * folder sekarang sudah cocok dan tidak perlu dipindah. Folder yang cocok: sama dengan nama yang
     * dibersihkan, atau nama itu diberi akhiran angka (_2, _3) karena ada instance lain yang sama namanya.
     * [takenLower] berisi semua nama folder yang sudah dipakai (huruf kecil), termasuk [current].
     */
    fun targetFolder(name: String, current: String, takenLower: Set<String>): String? {
        val base = sanitize(name)
        if (matches(base, current)) return null
        val target = unique(base, takenLower - current.lowercase())
        return if (target.equals(current, ignoreCase = true)) null else target
    }

    private fun matches(base: String, current: String): Boolean {
        if (current.equals(base, ignoreCase = true)) return true
        if (!current.startsWith("${base}_", ignoreCase = true)) return false
        val suffix = current.substring(base.length + 1)
        return suffix.isNotEmpty() && suffix.all { it in '0'..'9' }
    }
}
