package com.israadev.nuxlauncher.core.horizon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.File

/**
 * Jembatan "lagu yang sedang diputar" untuk mod Amber Lyrics di mesin Starling (docs/amber-lyrics.md).
 *
 * Tanpa izin khusus apa pun: tidak ada akses notifikasi, aksesibilitas, atau izin berbahaya lain. Yang dipakai hanya
 * penerima siaran (BroadcastReceiver) biasa untuk siaran "lagu berganti / jeda / putar" yang dikirim sebagian pemutar
 * musik (Spotify bila "Device Broadcast Status" menyala di pengaturannya, dan pemutar yang memakai siaran gaya
 * `com.android.music.*`). Penerima hidup hanya di proses game Starling, selama game terbuka, dan hanya menulis bila mod
 * Amber Lyrics dihidupkan pemain. Hasilnya satu berkas lokal, `config/amber_lyrics/now_playing.json` di folder game,
 * yang dibaca mod. Tidak ada yang dikirim ke mana pun dari sini; pencarian lirik dilakukan mod ke lrclib.net.
 *
 * Batasnya: pemutar yang tidak menyiarkan apa-apa (misalnya YouTube Music) tidak terbaca, dan siaran baru datang
 * saat ada kejadian (ganti lagu, jeda, putar), jadi lagu yang sudah berjalan saat game dibuka baru terbaca setelah
 * pemain menjeda lalu memutar sekali atau mengganti lagu.
 */
object NowPlayingBridge {
    const val FOLDER = "amber_lyrics"
    const val FILE = "now_playing.json"

    private const val MOD_ID = "amber_lyrics"

    // Siaran yang dikenal. Nama yang tidak pernah dikirim pemutar mana pun hanya tidak berbuat apa-apa.
    private val ACTIONS = listOf(
        "com.spotify.music.metadatachanged",
        "com.spotify.music.playbackstatechanged",
        "com.android.music.metachanged",
        "com.android.music.playstatechanged",
        "com.android.music.playbackcomplete",
        "com.sec.android.app.music.metachanged",
        "com.sec.android.app.music.playstatechanged",
        "com.miui.player.metachanged",
        "com.htc.music.metachanged",
        "com.sonyericsson.music.metachanged"
    )

    // Keadaan terakhir: siaran jeda/putar sering tidak membawa judul, jadi judul sebelumnya dipakai ulang.
    private var title = ""
    private var artist = ""
    private var album = ""
    private var durationMs = 0L
    private var positionMs = 0L
    private var positionAtMs = 0L
    private var playing = false

    private var receiver: BroadcastReceiver? = null
    private var registeredOn: Context? = null

    /** Berkas yang dibaca mod. Folder game sama dengan yang dipakai mesin (profil terpilih ikut dihormati). */
    fun file(context: Context): File =
        File(HorizonContent.gameHome(StarlingEngine.engineHome(context)), "config/$FOLDER/$FILE")

    /** Mulai mendengar siaran pemutar musik (dipanggil saat game Starling dibuka). */
    @Synchronized
    fun start(context: Context) {
        if (receiver != null) return
        val app = context.applicationContext ?: context
        // Sisa sesi lalu: tidak diketahui lagi benar atau tidak, lebih baik kosong daripada lirik yang salah.
        clearState(app)
        val created = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action ?: return
                val extras = intent.extras ?: Bundle.EMPTY
                handle(app, action, extras)
            }
        }
        val filter = IntentFilter()
        for (action in ACTIONS) filter.addAction(action)
        try {
            // Siaran datang dari aplikasi lain, jadi penerima harus "exported" (wajib dinyatakan di Android 14).
            ContextCompat.registerReceiver(app, created, filter, ContextCompat.RECEIVER_EXPORTED)
            receiver = created
            registeredOn = app
        } catch (_: Exception) {
            // Gagal mendaftar: lirik saja yang tidak muncul, game tidak terganggu.
        }
    }

    /** Berhenti mendengar dan menghapus berkas (game ditutup). */
    @Synchronized
    fun stop() {
        val app = registeredOn
        val current = receiver
        if (app != null && current != null) {
            try {
                app.unregisterReceiver(current)
            } catch (_: Exception) {
            }
        }
        receiver = null
        registeredOn = null
        if (app != null) clearState(app)
    }

    /** Mod hidup bila `mod.amber_lyrics:true` ada di horizon-mods.txt (dibaca tiap siaran; jarang dan kecil). */
    private fun modEnabled(context: Context): Boolean =
        try {
            val game = HorizonContent.gameHome(StarlingEngine.engineHome(context))
            val list = File(game, HorizonMods.LIST_FILE)
            list.isFile && list.length() < 1_000_000L && HorizonMods.parseList(list.readText()).enabled.contains(MOD_ID)
        } catch (_: Exception) {
            false
        }

    @Synchronized
    private fun handle(context: Context, action: String, extras: Bundle) {
        try {
            if (!modEnabled(context)) return
            if (action.endsWith(".playbackcomplete")) {
                clearState(context)
                return
            }
            // Siaran tanpa judul hanya mengabarkan keadaan (jeda/putar): judul dan artis sebelumnya dipertahankan.
            val eventTitle = text(extras, "track", "title")
            val newTitle = eventTitle.ifBlank { title }
            if (newTitle.isBlank()) return
            val newArtist =
                if (eventTitle.isBlank()) artist else text(extras, "artist").ifBlank { if (eventTitle == title) artist else "" }
            val sameTrack = newTitle == title && newArtist == artist
            val now = System.currentTimeMillis()

            val reportedPlaying = flag(extras, "playing")
            val nowPlaying = reportedPlaying ?: if (sameTrack) playing else true

            // Posisi: yang dilaporkan pemutar bila ada; selain itu lagu baru mulai dari 0, dan lagu yang sama
            // melanjutkan dari perkiraan terakhir.
            val reported = number(extras, "playbackPosition", "position", "pos")
            // Spotify membawa `timeSent`: posisi berlaku pada saat itu, bukan saat siaran sampai.
            val sentAt = number(extras, "timeSent")
            val at = if (sentAt > 0L && kotlin.math.abs(now - sentAt) < 10 * 60 * 1000L) sentAt else now
            val position = when {
                reported >= 0L -> reported
                !sameTrack -> 0L
                playing -> positionMs + (now - positionAtMs).coerceAtLeast(0L)
                else -> positionMs
            }

            val newAlbum = text(extras, "album").ifBlank { if (sameTrack) album else "" }
            val reportedLength = number(extras, "length", "duration", "dur")
            val newDuration = if (reportedLength > 0L) reportedLength else if (sameTrack) durationMs else 0L

            title = newTitle
            artist = newArtist
            album = newAlbum
            durationMs = newDuration
            positionMs = position
            positionAtMs = at
            playing = nowPlaying
            writeState(context, at)
        } catch (_: Exception) {
            // Siaran aneh dari pemutar: diabaikan.
        }
    }

    private fun writeState(context: Context, updatedAtMs: Long) {
        try {
            val target = file(context)
            val dir = target.parentFile ?: return
            dir.mkdirs()
            val json = JSONObject()
                .put("title", title)
                .put("artist", artist)
                .put("album", album)
                .put("duration_ms", durationMs.coerceAtLeast(0L))
                .put("position_ms", positionMs.coerceAtLeast(0L))
                .put("playing", playing)
                .put("updated_at_ms", updatedAtMs)
            // Ditulis ke berkas sementara lalu dipindah, supaya mod tidak pernah membaca separuh isi.
            val temp = File(dir, "$FILE.tmp")
            temp.writeText(json.toString())
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
        } catch (_: Exception) {
            // Penyimpanan penuh atau folder tak bisa ditulis: lirik saja yang tidak muncul.
        }
    }

    /** Melupakan lagu dan menghapus berkas: tidak ada lagu yang diketahui. */
    private fun clearState(context: Context) {
        title = ""
        artist = ""
        album = ""
        durationMs = 0L
        positionMs = 0L
        positionAtMs = 0L
        playing = false
        try {
            val target = file(context)
            if (target.isFile) target.delete()
        } catch (_: Exception) {
        }
    }

    private fun text(extras: Bundle, vararg keys: String): String {
        for (key in keys) {
            val value = extras.getString(key)?.trim()
            if (!value.isNullOrEmpty()) return value
        }
        return ""
    }

    // Pemutar berbeda mengirim angka sebagai Int, Long, atau teks; Bundle.getLong() menolak Int, jadi dibaca apa adanya.
    @Suppress("DEPRECATION")
    private fun number(extras: Bundle, vararg keys: String): Long {
        for (key in keys) {
            if (!extras.containsKey(key)) continue
            when (val value = extras.get(key)) {
                is Int -> return value.toLong()
                is Long -> return value
                is Float -> return value.toLong()
                is Double -> return value.toLong()
                is String -> value.trim().toLongOrNull()?.let { return it }
                else -> {}
            }
        }
        return -1L
    }

    @Suppress("DEPRECATION")
    private fun flag(extras: Bundle, key: String): Boolean? {
        if (!extras.containsKey(key)) return null
        return when (val value = extras.get(key)) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            is Int -> value != 0
            else -> null
        }
    }
}
