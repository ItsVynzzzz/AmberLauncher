package com.israadev.nuxlauncher.discord

import java.util.Locale

/**
 * Teks Rich Presence per bahasa. Teks ini tampil di profil Discord ORANG LAIN, jadi mengikuti bahasa pemain
 * (bahasa yang dipilih di Pengaturan > Tampilan, atau bahasa HP bila memilih "Ikuti sistem"), dan jatuh ke bahasa
 * Inggris bila bahasanya belum ada di sini. Nama game, versi, dan loader (Minecraft, Fabric, ...) tidak diterjemahkan.
 *
 * Terjemahan ditulis oleh AI dan belum ditinjau penutur asli: perbaiki langsung di [table] bila ada yang janggal.
 * Setiap teks "onServer" wajib memuat %s (diganti alamat server).
 */
internal object PresenceText {
    class Strings(
        /** "On server: mc.contoh.com" */
        val onServer: String,
        /** Server yang alamatnya tidak ditampilkan (jaringan lokal). */
        val onPrivateServer: String,
        val singleplayer: String,
        /** Baris atas saat pemain baru di launcher. */
        val launcherDetails: String,
        val launcherState: String
    )

    const val FALLBACK = "en"

    /** Bahasa pilihan di aplikasi yang dipakai apa adanya (selain "system"). */
    private val explicitChoices = setOf("id", "en", "es", "vi", "pt", "th", "zh", "fil")

    /**
     * Memilih kode bahasa dari [table]. [choice] adalah pilihan bahasa di aplikasi ("system" = ikuti HP),
     * [locale] bahasa HP.
     */
    fun resolve(choice: String, locale: Locale): String {
        if (choice in explicitChoices) return choice
        val raw = locale.language.lowercase()
        val lang = when (raw) {
            "in" -> "id"
            "tl" -> "fil"
            else -> raw
        }
        if (lang == "zh") {
            val traditional = locale.script.equals("Hant", ignoreCase = true) ||
                locale.country.uppercase() in setOf("TW", "HK", "MO")
            return if (traditional) "zh-TW" else "zh"
        }
        return if (table.containsKey(lang)) lang else FALLBACK
    }

    fun strings(lang: String): Strings = table[lang] ?: table.getValue(FALLBACK)

    val languages: Set<String> get() = table.keys

    private val table: Map<String, Strings> = mapOf(
        "en" to Strings("On server: %s", "On a private server", "Singleplayer", "Getting ready to play", "In the launcher"),
        "id" to Strings("Di server: %s", "Di server pribadi", "Pemain tunggal", "Bersiap bermain", "Di launcher"),
        "es" to Strings("En el servidor: %s", "En un servidor privado", "Un jugador", "Preparándose para jugar", "En el launcher"),
        "pt" to Strings("No servidor: %s", "Em um servidor privado", "Um jogador", "Preparando para jogar", "No launcher"),
        "fr" to Strings("Sur le serveur : %s", "Sur un serveur privé", "Solo", "Prépare une partie", "Dans le launcher"),
        "de" to Strings("Auf dem Server: %s", "Auf einem privaten Server", "Einzelspieler", "Macht sich spielbereit", "Im Launcher"),
        "it" to Strings("Sul server: %s", "Su un server privato", "Giocatore singolo", "Si prepara a giocare", "Nel launcher"),
        "nl" to Strings("Op server: %s", "Op een privéserver", "Singleplayer", "Maakt zich klaar om te spelen", "In de launcher"),
        "pl" to Strings("Na serwerze: %s", "Na serwerze prywatnym", "Gra jednoosobowa", "Przygotowuje się do gry", "W launcherze"),
        "ru" to Strings("На сервере: %s", "На частном сервере", "Одиночная игра", "Готовится к игре", "В лаунчере"),
        "uk" to Strings("На сервері: %s", "На приватному сервері", "Одиночна гра", "Готується до гри", "У лаунчері"),
        "tr" to Strings("Sunucuda: %s", "Özel bir sunucuda", "Tek oyunculu", "Oynamaya hazırlanıyor", "Başlatıcıda"),
        "ar" to Strings("على الخادم: %s", "على خادم خاص", "لاعب واحد", "يستعد للعب", "في المشغّل"),
        "hi" to Strings("सर्वर पर: %s", "निजी सर्वर पर", "सिंगलप्लेयर", "खेलने की तैयारी में", "लॉन्चर में"),
        "vi" to Strings("Trên máy chủ: %s", "Trên máy chủ riêng", "Chơi đơn", "Đang chuẩn bị chơi", "Trong launcher"),
        "th" to Strings("ในเซิร์ฟเวอร์: %s", "ในเซิร์ฟเวอร์ส่วนตัว", "เล่นคนเดียว", "กำลังเตรียมเล่น", "ในแลนเชอร์"),
        "fil" to Strings("Sa server: %s", "Sa pribadong server", "Singleplayer", "Naghahanda sa paglalaro", "Nasa launcher"),
        "ja" to Strings("サーバー: %s", "プライベートサーバー", "シングルプレイ", "プレイの準備中", "ランチャー内"),
        "ko" to Strings("서버: %s", "비공개 서버", "싱글플레이", "플레이 준비 중", "런처에서"),
        "zh" to Strings("服务器：%s", "私人服务器", "单人游戏", "准备开始游戏", "在启动器中"),
        "zh-TW" to Strings("伺服器：%s", "私人伺服器", "單人遊戲", "準備開始遊戲", "在啟動器中")
    )
}
