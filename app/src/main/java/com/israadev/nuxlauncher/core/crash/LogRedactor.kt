package com.israadev.nuxlauncher.core.crash

/**
 * Menyamarkan token dan kata sandi di dalam teks log sebelum log dibagikan atau diunggah,
 * supaya akun pengguna tidak bocor lewat log yang ditempel di forum atau layanan pastebin.
 */
object LogRedactor {

    // Argumen peluncuran game: --accessToken xxx, --session xxx, dan sejenisnya
    private val ARG_PATTERN = Regex("""(?i)(--(?:accessToken|session|xuid|clientId))\s+\S+""")

    // Pasangan kunci-nilai: access_token=xxx, "refresh_token": "xxx", password: xxx
    private val KEY_VALUE_PATTERN =
        Regex("""(?i)((?:access|refresh|client|id)[_-]?token|authorization|password|secret)(["']?\s*[:=]\s*["']?)[^\s"',;}&]+""")

    // Header Authorization: seluruh nilainya (Bearer, Basic, dan sejenisnya) sampai akhir baris
    private val AUTH_HEADER_PATTERN = Regex("""(?i)(authorization["']?\s*[:=]\s*)[^\r\n]+""")

    // Header "Bearer xxx"
    private val BEARER_PATTERN = Regex("""(?i)(bearer\s+)[A-Za-z0-9._~+/=-]{8,}""")

    // Token berbentuk JWT (tiga bagian base64 dipisah titik)
    private val JWT_PATTERN = Regex("""eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{4,}""")

    // Pemeriksaan cepat: baris tanpa kata-kata ini tidak perlu diproses regex sama sekali
    private val HINTS = listOf("token", "--session", "--xuid", "--clientid", "authorization", "password", "secret", "bearer", "eyj")

    fun redactLine(line: String): String {
        val lower = line.lowercase()
        if (HINTS.none { lower.contains(it) }) return line

        var result = line
        result = ARG_PATTERN.replace(result) { "${it.groupValues[1]} ***" }
        result = AUTH_HEADER_PATTERN.replace(result) { "${it.groupValues[1]}***" }
        result = BEARER_PATTERN.replace(result) { "${it.groupValues[1]}***" }
        result = KEY_VALUE_PATTERN.replace(result) { "${it.groupValues[1]}${it.groupValues[2]}***" }
        result = JWT_PATTERN.replace(result, "***")
        return result
    }

    fun redact(text: String): String {
        return text.lineSequence().joinToString("\n") { redactLine(it) }
    }
}
