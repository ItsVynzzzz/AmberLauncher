package com.israadev.nuxlauncher.discord

/** Hasil membaca satu baris log game untuk Rich Presence. */
internal sealed class ServerEvent {
    /** Pemain mulai tersambung ke server multiplayer. [display] null = alamatnya jaringan lokal (tidak ditampilkan). */
    data class Joined(val display: String?) : ServerEvent()

    /** Pemain membuka dunia singleplayer. */
    object Singleplayer : ServerEvent()

    /** Pemain terputus dari server atau menutup dunia singleplayer. */
    object Left : ServerEvent()
}

/**
 * Membaca server multiplayer dari log game (Java). Launcher tidak ikut memilih server (pemain memilihnya di menu
 * Minecraft), jadi satu-satunya petunjuknya baris log Minecraft seperti "Connecting to mc.contoh.com, 25565".
 *
 * Keterbatasan: Minecraft tidak mencatat saat pemain keluar dari server ke menu utama dengan sengaja, jadi
 * alamat terakhir bertahan sampai pemain masuk server lain, membuka dunia singleplayer, atau terputus
 * oleh server. Alamat jaringan lokal (LAN, localhost, IPv6) tidak pernah ditampilkan.
 */
internal object ServerLog {
    private const val CONNECTING = "Connecting to "
    private const val DEFAULT_PORT = 25565
    private val connectPattern = Regex("""Connecting to ([A-Za-z0-9._\-\[\]:]{1,100}), (\d{1,5})\s*$""")
    private val ipv4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

    /** Mengembalikan null bila baris ini tidak menyangkut server. Penyaring murahnya jalan dulu: hampir semua baris dilewati. */
    fun parse(text: String): ServerEvent? {
        val at = text.indexOf(CONNECTING)
        if (at >= 0) {
            val m = connectPattern.find(text, at) ?: return null
            val port = m.groupValues[2].toIntOrNull() ?: return null
            if (port !in 1..65535) return null
            return ServerEvent.Joined(label(m.groupValues[1], port))
        }
        if (text.contains("Starting integrated minecraft server")) return ServerEvent.Singleplayer
        if (text.contains("Client disconnected with reason") || text.contains("Stopping singleplayer server")) {
            return ServerEvent.Left
        }
        return null
    }

    /** Alamat yang aman ditampilkan, atau null bila alamatnya jaringan lokal. Port bawaan disembunyikan. */
    fun label(host: String, port: Int): String? {
        val h = host.lowercase()
        if (isPrivate(h)) return null
        return if (port == DEFAULT_PORT) h else "$h:$port"
    }

    private fun isPrivate(h: String): Boolean {
        if (h == "localhost" || h.endsWith(".local") || h.endsWith(".localhost") || h.endsWith(".lan")) return true
        if (h.startsWith("[") || h.contains(':')) return true // IPv6: tidak ditampilkan
        val m = ipv4.matchEntire(h) ?: return false
        val a = m.groupValues[1].toInt()
        val b = m.groupValues[2].toInt()
        return a == 0 || a == 10 || a == 127 ||
            (a == 192 && b == 168) ||
            (a == 172 && b in 16..31) ||
            (a == 169 && b == 254) ||
            (a == 100 && b in 64..127)
    }
}
