package com.israadev.nuxlauncher.core.update

import com.google.gson.JsonParser

/**
 * Catatan "Yang baru": butir-butir perubahan per bahasa. Tanpa ketergantungan Android, jadi bisa diuji di JVM.
 *
 * Datanya dibuat oleh scripts/release-notes.py dari berkas .txt di changelog/entries dan sampai ke aplikasi lewat dua jalan:
 *  - berkas assets/whatsnew.json di dalam APK (catatan versi yang terpasang, dibaca layar Tentang dan dialog
 *    setelah pembaruan);
 *  - blok tersembunyi `<!-- amber-whatsnew:v1 ... -->` di isi Release GitHub (catatan versi baru, dibaca pop-up pembaruan).
 * Keduanya berisi JSON yang sama: {"v":1,"version":"1.2.4","lang":{"id":[["new","teks"],...],"en":[...],...}}.
 */
object WhatsNew {
    const val ASSET = "whatsnew.json"

    enum class Kind { NEW, IMPROVED, FIXED }

    class Item(val kind: Kind, val text: String)

    class Notes(val version: String, private val byLang: Map<String, List<Item>>) {
        /** Butir untuk [lang] (kode bahasa launcher); bahasa yang tak ada jatuh ke Inggris, lalu Indonesia. */
        fun forLanguage(lang: String): List<Item> =
            byLang[lang]?.takeIf { it.isNotEmpty() } ?: byLang["en"] ?: byLang["id"] ?: emptyList()

        fun isEmpty(): Boolean = byLang.values.all { it.isEmpty() }
    }

    // Awalan "https://" pada blok membuat aplikasi lama menyaringnya dari pop-up pembaruan (lihat ReleaseParser).
    private val BLOCK = Regex("<!--\\s*amber-whatsnew:v1\\s+\\S+\\s+(\\{.*?\\})\\s*-->", RegexOption.DOT_MATCHES_ALL)

    /** Mengurai JSON catatan; null bila bentuknya tidak dikenal atau kosong. */
    fun fromJson(json: String): Notes? {
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            if (root.get("v")?.asInt != 1) return null
            val map = HashMap<String, List<Item>>()
            for ((lang, list) in root.getAsJsonObject("lang").entrySet()) {
                val items = ArrayList<Item>()
                for (e in list.asJsonArray) {
                    val pair = e.asJsonArray
                    val text = pair[1].asString.trim()
                    if (text.isNotEmpty()) items += Item(kindOf(pair[0].asString), text)
                }
                map[lang] = items
            }
            Notes(root.get("version")?.asString.orEmpty(), map).takeUnless { it.isEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    /** Mengambil catatan dari isi Release GitHub; null bila Release tidak membawa blok catatan (rilis lama). */
    fun fromReleaseBody(body: String): Notes? =
        BLOCK.find(body)?.groupValues?.get(1)?.let { fromJson(it) }

    private fun kindOf(code: String): Kind = when (code) {
        "new" -> Kind.NEW
        "fix" -> Kind.FIXED
        else -> Kind.IMPROVED
    }
}
