package com.israadev.nuxlauncher.discord

/** Pilihan pemain untuk isi presence saat bermain. Semuanya mati kecuali ikon server (yang hanya berlaku bila server tampil). */
data class DiscordOptions(
    val showServer: Boolean = false,
    val showInstance: Boolean = false,
    val serverIcon: Boolean = true
)

/** Keadaan server yang terbaca dari log game. */
internal sealed class ServerState {
    object None : ServerState()
    object Singleplayer : ServerState()

    /** [display] null = alamatnya jaringan lokal dan tidak ditampilkan. */
    data class Server(val display: String?) : ServerState()
}

/**
 * Menyusun isi Rich Presence: teks (sesuai bahasa), waktu, dan gambar. Tanpa Android, jadi seluruh kombinasinya diuji.
 *
 * Tampilan saat bermain, mengikuti contoh launcher lain:
 *  - baris atas  : nama instance (bila diizinkan) atau "Minecraft 1.21.11 · Fabric"
 *  - baris bawah : "On server: mc.contoh.com", "Singleplayer", atau (bila nama instance tampil) "Minecraft 1.21.11 · Fabric"
 *  - gambar besar: ikon server (bila server tampil dan diizinkan) dan logo AmberLauncher sebagai gambar kecil;
 *    selain itu logo AmberLauncher sebagai gambar besar.
 */
internal object PresenceComposer {
    const val APP_NAME = "AmberLauncher"

    /** Logo launcher dari repo publik; Discord mengambilnya lewat URL, jadi tidak perlu mengunggah aset di Developer Portal. */
    const val LOGO_URL =
        "https://raw.githubusercontent.com/ItsVynzzzz/AmberLauncher/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"

    /** Layanan pihak ketiga yang mengembalikan ikon server (PNG 64x64) dari alamatnya. */
    const val SERVER_ICON_URL = "https://api.mcsrvstat.us/icon/"

    private val safeAddress = Regex("""^[a-z0-9._-]{1,100}(:\d{1,5})?$""")

    fun game(
        mcVersion: String,
        loader: String,
        instanceName: String,
        options: DiscordOptions,
        lang: String,
        server: ServerState,
        startMs: Long
    ): PresenceInfo {
        val s = PresenceText.strings(lang)
        val version = mcVersion.trim().takeIf { it.isNotEmpty() && !it.equals("Unknown", ignoreCase = true) }
        val minecraft = if (version != null) "Minecraft $version" else "Minecraft"
        val summary = "$minecraft · ${loaderLabel(loader)}"
        val instance = if (options.showInstance) instanceName.trim() else ""

        val details = instance.ifEmpty { summary }
        val state = when (server) {
            is ServerState.Server -> server.display?.let { s.onServer.replace("%s", it) } ?: s.onPrivateServer
            ServerState.Singleplayer -> s.singleplayer
            ServerState.None -> if (instance.isNotEmpty()) summary else ""
        }

        val address = (server as? ServerState.Server)?.display
        if (options.serverIcon && address != null && safeAddress.matches(address)) {
            return PresenceInfo(
                details, state, startMs,
                largeImage = SERVER_ICON_URL + address, largeText = address,
                smallImage = LOGO_URL, smallText = APP_NAME
            )
        }
        return PresenceInfo(details, state, startMs, largeImage = LOGO_URL, largeText = APP_NAME)
    }

    fun launcher(lang: String, startMs: Long): PresenceInfo {
        val s = PresenceText.strings(lang)
        return PresenceInfo(s.launcherDetails, s.launcherState, startMs, largeImage = LOGO_URL, largeText = APP_NAME)
    }

    fun loaderLabel(loader: String): String = when (loader.trim().lowercase()) {
        "", "vanilla" -> "Vanilla"
        "fabric" -> "Fabric"
        "forge" -> "Forge"
        "neoforge", "neo_forge", "neo-forge" -> "NeoForge"
        "quilt" -> "Quilt"
        "horizon" -> "Horizon (Rust)"
        "starling" -> "Starling (Rust)"
        else -> loader.trim().replaceFirstChar { it.uppercase() }
    }
}
