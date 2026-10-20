package com.israadev.nuxlauncher.ui.i18n

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import java.util.Locale

/**
 * Bahasa tampilan launcher. Teks asli aplikasi berbahasa Indonesia; bahasa lain dimuat dari
 * kamus berkas .tsv di folder assets/lang/<kode>/ (lihat [Translator]). Bahasa Indonesia tidak memuat apa pun
 * dan [t] langsung mengembalikan teksnya, jadi tidak ada beban tambahan untuk pengguna Indonesia.
 *
 * Pilihan pengguna: [SYSTEM] (ikuti bahasa HP), [ID], [EN], atau [ES]. Pada pilihan [SYSTEM], HP
 * berbahasa Indonesia memakai Indonesia, HP berbahasa Spanyol memakai Spanyol, dan bahasa lain
 * jatuh ke bahasa Inggris. Bahasa baru cukup ditambah di [DICTIONARY_LANGS] dan folder kamusnya.
 */
object Tr {
    const val SYSTEM = "system"
    const val ID = "id"
    const val EN = "en"
    const val ES = "es"

    /** Bahasa yang punya kamus di assets/lang/<kode>/ (selain Indonesia, bahasa asli teksnya). */
    private val DICTIONARY_LANGS = setOf(EN, ES)

    private const val PREFS_NAME = "amber_launcher_prefs"
    private const val KEY_LANGUAGE = "language"

    /** null = teks asli (Indonesia). */
    @Volatile
    private var translator: Translator? = null

    /** Pilihan pengguna saat ini: [SYSTEM], [ID], [EN], atau [ES]. */
    @Volatile
    var choice: String = SYSTEM
        private set

    /** Bahasa yang benar-benar dipakai: [ID], [EN], atau [ES]. */
    @Volatile
    var effective: String = ID
        private set

    /** Naik setiap kali bahasa berubah. Dibaca hanya di akar UI agar layar digambar ulang sekali. */
    internal val version = mutableIntStateOf(0)

    /** Dipanggil sekali saat proses aplikasi dimulai (tiap proses, termasuk proses game). */
    fun init(context: Context) {
        val saved = try {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, SYSTEM)
        } catch (_: Throwable) {
            SYSTEM
        }
        choice = normalize(saved)
        apply(context)
    }

    /** Mengganti bahasa, menyimpannya, dan membuat seluruh layar menyesuaikan. */
    fun setLanguage(context: Context, newChoice: String) {
        val c = normalize(newChoice)
        if (c == choice) return
        choice = c
        try {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LANGUAGE, c)
                .apply()
        } catch (_: Throwable) {
        }
        apply(context)
        version.intValue = version.intValue + 1
    }

    /** Menerjemahkan satu teks. Teks yang tidak ada di kamus dikembalikan apa adanya. */
    fun t(text: String): String {
        val tr = translator ?: return text
        return tr.translate(text)
    }

    private fun normalize(value: String?): String = when {
        value == null -> SYSTEM
        value == ID || value in DICTIONARY_LANGS -> value
        else -> SYSTEM
    }

    private fun resolve(c: String): String {
        if (c == ID || c in DICTIONARY_LANGS) return c
        val lang = Locale.getDefault().language
        return when {
            lang == "in" || lang == "id" -> ID
            lang in DICTIONARY_LANGS -> lang
            else -> EN
        }
    }

    private fun apply(context: Context) {
        val lang = resolve(choice)
        effective = lang
        translator = if (lang == ID) null else load(context, lang)
    }

    private fun load(context: Context, lang: String): Translator? {
        return try {
            val assets = context.applicationContext.assets
            val dir = "lang/$lang"
            val names = (assets.list(dir) ?: emptyArray()).filter { it.endsWith(".tsv") }.sorted()
            if (names.isEmpty()) return null
            val lines = ArrayList<String>(1024)
            for (name in names) {
                assets.open("$dir/$name").bufferedReader(Charsets.UTF_8).use { reader ->
                    reader.forEachLine { lines.add(it) }
                }
            }
            Translator.parse(lines)
        } catch (_: Throwable) {
            null
        }
    }
}
