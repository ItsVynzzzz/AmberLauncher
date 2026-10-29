package com.israadev.nuxlauncher.ui.i18n

/**
 * Penerjemah teks sederhana tanpa ketergantungan Android, sehingga mudah diuji.
 *
 * Teks asli aplikasi ditulis dalam bahasa Indonesia. Kamus bahasa lain berisi pasangan
 * "teks Indonesia -> terjemahan", satu pasangan per baris, dipisah satu karakter TAB:
 *
 *     Simpan<TAB>Save
 *     Instance {0} berhasil dibuat!<TAB>Instance {0} created!
 *
 * - Baris kosong dan baris berawalan # diabaikan.
 * - Di dalam teks, \n = baris baru, \t = TAB, \\ = garis miring, \uXXXX = karakter unicode.
 * - {0}, {1}, ... adalah isian yang berubah-ubah (nama instance, angka, dan sebagainya).
 *   Kuncinya dicocokkan sebagai pola dan isiannya disalin ke terjemahan.
 * - {~0}, {~1}, ... di KUNCI berarti isian itu sendiri juga diterjemahkan lewat kamus (untuk
 *   pesan galat yang dibungkus pesan lain). Di terjemahan tetap ditulis {0}, {1}, ...
 *
 * Teks yang tidak ada di kamus dikembalikan apa adanya (tetap Indonesia), jadi kamus yang
 * belum lengkap tidak pernah merusak tampilan.
 */
class Translator private constructor(
    private val exact: Map<String, String>,
    private val templates: List<Template>
) {

    /** Satu pola berisian: [anchor] = potongan teks tetap terpanjang, untuk menyaring cepat. */
    private class Template(
        private val pattern: String,
        val anchor: String,
        val parts: List<Any>,
        val nested: Set<Int>,
        /** Jumlah huruf tetap di kunci. Pola yang lebih spesifik (lebih besar) dicoba lebih dulu. */
        val weight: Int
    ) {
        /**
         * Regex baru dikompilasi saat pertama kali dibutuhkan, yaitu ketika sebuah teks lolos saringan
         * [anchor]. Kamus punya ratusan pola dan hampir semuanya tidak pernah cocok dalam satu sesi, jadi
         * mengompilasi semuanya saat aplikasi dibuka (di tiap proses, termasuk proses game) hanya membuang
         * waktu dan memori. Hanya dibaca dari translateByTemplate yang sudah @Synchronized.
         * null = pola tidak valid, dilewati.
         */
        val regex: Regex? by lazy(LazyThreadSafetyMode.NONE) {
            try {
                Regex(pattern, RegexOption.DOT_MATCHES_ALL)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun render(t: Template, match: MatchResult, depth: Int): String {
        val sb = StringBuilder()
        for (p in t.parts) {
            if (p is Int) {
                val g = match.groups[p + 1]
                if (g != null) {
                    if (depth < MAX_DEPTH && p in t.nested) sb.append(translateAt(g.value, depth + 1))
                    else sb.append(g.value)
                }
            } else {
                sb.append(p as String)
            }
        }
        return sb.toString()
    }

    /** Hasil pencocokan pola yang sudah pernah dihitung (hemat kerja untuk teks yang berulang). */
    private val memo = HashMap<String, String>()

    val size: Int get() = exact.size + templates.size

    fun translate(text: String): String = translateAt(text, 0)

    private fun translateAt(text: String, depth: Int): String {
        if (text.isEmpty()) return text
        val hit = exact[text]
        if (hit != null) return hit
        if (templates.isEmpty()) return text
        return translateByTemplate(text, depth)
    }

    @Synchronized
    private fun translateByTemplate(text: String, depth: Int): String {
        val cached = memo[text]
        if (cached != null) return cached
        var result = text
        for (t in templates) {
            if (t.anchor.isNotEmpty() && !text.contains(t.anchor)) continue
            val m = t.regex?.matchEntire(text) ?: continue
            result = render(t, m, depth)
            break
        }
        if (memo.size >= MEMO_MAX) memo.clear()
        memo[text] = result
        return result
    }

    companion object {
        private const val MEMO_MAX = 512
        private const val MAX_DEPTH = 2
        private val PLACEHOLDER = Regex("\\{(~?)(\\d+)\\}")
        private val VALUE_PLACEHOLDER = Regex("\\{(\\d+)\\}")

        /** Membaca baris-baris kamus (lihat penjelasan di atas) menjadi sebuah [Translator]. */
        fun parse(lines: List<String>): Translator {
            val exact = HashMap<String, String>()
            val templates = ArrayList<Template>()
            for (raw in lines) {
                if (raw.isEmpty() || raw[0] == '#') continue
                val tab = raw.indexOf('\t')
                if (tab <= 0) continue
                val key = unescape(raw.substring(0, tab))
                val value = unescape(raw.substring(tab + 1))
                if (value.isEmpty()) continue
                if (PLACEHOLDER.containsMatchIn(key)) {
                    val t = buildTemplate(key, value)
                    if (t != null) templates.add(t)
                } else {
                    exact[key] = value
                }
            }
            // Pola yang lebih spesifik dicoba lebih dulu, supaya "Versi {0}" tidak menimpa
            // "Versi {0} tidak ditemukan". sortedByDescending stabil: urutan sama tetap seperti di kamus.
            return Translator(exact, templates.sortedByDescending { it.weight })
        }

        private fun buildTemplate(key: String, value: String): Template? {
            val pattern = StringBuilder("^")
            var anchor = ""
            var last = 0
            var groups = 0
            var weight = 0
            val nested = HashSet<Int>()
            for (m in PLACEHOLDER.findAll(key)) {
                val literal = key.substring(last, m.range.first)
                if (literal.length > anchor.length) anchor = literal
                weight += literal.length
                pattern.append(Regex.escape(literal)).append("(.+?)")
                if (m.groupValues[1] == "~") nested.add(m.groupValues[2].toInt())
                groups++
                last = m.range.last + 1
            }
            val tail = key.substring(last)
            if (tail.length > anchor.length) anchor = tail
            weight += tail.length
            pattern.append(Regex.escape(tail)).append("$")

            val parts = ArrayList<Any>()
            var pos = 0
            for (m in VALUE_PLACEHOLDER.findAll(value)) {
                if (m.range.first > pos) parts.add(value.substring(pos, m.range.first))
                val index = m.groupValues[1].toInt()
                if (index >= groups) return null
                parts.add(index)
                pos = m.range.last + 1
            }
            if (pos < value.length) parts.add(value.substring(pos))

            // Regex-nya sengaja belum dikompilasi di sini (lihat Template.regex).
            return Template(pattern.toString(), anchor, parts, nested, weight)
        }

        /** Mengubah \n, \t, \\ dan \uXXXX di dalam satu bidang kamus menjadi karakter aslinya. */
        fun unescape(s: String): String {
            if (s.indexOf('\\') < 0) return s
            val sb = StringBuilder(s.length)
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c != '\\' || i + 1 >= s.length) {
                    sb.append(c)
                    i++
                    continue
                }
                val n = s[i + 1]
                when (n) {
                    'n' -> { sb.append('\n'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    'u' -> {
                        val hex = if (i + 6 <= s.length) s.substring(i + 2, i + 6) else ""
                        val code = hex.toIntOrNull(16)
                        if (code != null) {
                            sb.append(code.toChar())
                            i += 6
                        } else {
                            sb.append(c)
                            i++
                        }
                    }
                    else -> { sb.append(c); i++ }
                }
            }
            return sb.toString()
        }
    }
}
