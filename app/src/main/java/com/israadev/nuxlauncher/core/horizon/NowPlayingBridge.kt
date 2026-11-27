package com.israadev.nuxlauncher.core.horizon

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import org.json.JSONObject
import java.io.File

/**
 * Jembatan "lagu yang sedang diputar" untuk mod Amber Lyrics di mesin Starling (docs/amber-lyrics.md).
 *
 * Android memberi daftar sesi media (Spotify, YouTube Music, pemutar bawaan, dan kawan-kawan) hanya kepada layanan
 * pendengar notifikasi yang diizinkan pengguna. [NowPlayingListenerService] memakai izin itu untuk satu hal saja:
 * membaca judul, artis, album, durasi, dan posisi lagu, lalu menulisnya ke `config/amber_lyrics/now_playing.json` di
 * folder game Starling. Mod di dalam mesin membaca berkas itu dan mencari lirik. Layanan tidak membaca isi notifikasi,
 * tidak menulis apa pun selain berkas itu, dan tidak punya pekerjaan terjadwal: ia bergerak hanya saat pemutar
 * berganti lagu, jeda, atau memutar, jadi tidak membebani game.
 */
object NowPlayingBridge {
    const val FOLDER = "amber_lyrics"
    const val FILE = "now_playing.json"

    /** Berkas yang dibaca mod. Folder game sama dengan yang dipakai mesin (profil terpilih ikut dihormati). */
    fun file(context: Context): File =
        File(HorizonContent.gameHome(StarlingEngine.engineHome(context)), "config/$FOLDER/$FILE")

    /** True bila pengguna sudah memberi Amber "Akses notifikasi" di pengaturan Android. */
    fun hasAccess(context: Context): Boolean =
        try {
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        } catch (_: Exception) {
            false
        }

    /** Membuka halaman "Akses notifikasi" milik Android; pengguna memilih Amber di sana. */
    fun openAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // HP yang tidak punya halaman itu: pengguna membukanya sendiri dari Pengaturan.
        }
    }

    // Isi yang terakhir ditulis: lagu yang sama dengan posisi yang sama tidak perlu menulis ulang.
    private var lastWritten: String? = null

    /** Menulis keadaan [controller] ke berkas, atau menghapus berkas bila tidak ada lagu. */
    @Synchronized
    fun write(context: Context, controller: MediaController?) {
        try {
            // Starling belum pernah dipakai: tidak membuat folder datanya hanya demi berkas ini.
            if (!StarlingEngine.engineHome(context).isDirectory) return
            val target = file(context)
            val metadata = controller?.metadata
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim().orEmpty()
            if (controller == null || metadata == null || title.isEmpty()) {
                clear(context)
                return
            }
            val state = controller.playbackState
            val playing = state?.state == PlaybackState.STATE_PLAYING
            var position = (state?.position ?: 0L).coerceAtLeast(0L)
            if (playing && state != null && state.lastPositionUpdateTime > 0L) {
                // Posisi yang dilaporkan sudah beberapa saat lalu: majukan sebanyak waktu yang lewat.
                val elapsed = (SystemClock.elapsedRealtime() - state.lastPositionUpdateTime).coerceAtLeast(0L)
                position += (elapsed * state.playbackSpeed).toLong()
            }
            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: ""
            val json = JSONObject()
                .put("title", title)
                .put("artist", artist.trim())
                .put("album", metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)?.trim().orEmpty())
                .put("duration_ms", metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L))
                .put("position_ms", position)
                .put("playing", playing)
                .put("updated_at_ms", System.currentTimeMillis())
            val text = json.toString()
            // Sama kecuali cap waktunya: tidak perlu menulis ulang.
            val comparable = text.replace(Regex("\"updated_at_ms\":\\d+"), "")
            if (comparable == lastWritten && target.isFile) return
            val dir = target.parentFile ?: return
            dir.mkdirs()
            // Ditulis ke berkas sementara lalu dipindah, supaya mod tidak pernah membaca separuh isi.
            val temp = File(dir, "$FILE.tmp")
            temp.writeText(text)
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
            lastWritten = comparable
        } catch (_: Exception) {
            // Penyimpanan penuh atau folder tak bisa ditulis: lirik saja yang tidak muncul, game tidak terganggu.
        }
    }

    /** Menghapus berkas: tidak ada lagu yang diputar (atau akses dicabut). */
    @Synchronized
    fun clear(context: Context) {
        lastWritten = null
        try {
            val target = file(context)
            if (target.isFile) target.delete()
        } catch (_: Exception) {
        }
    }
}

/**
 * Pendengar notifikasi yang hanya dipakai untuk sesi media (lihat [NowPlayingBridge]). Berjalan di proses utama
 * Amber, hanya selama pengguna memberi aksesnya.
 */
class NowPlayingListenerService : NotificationListenerService() {
    private val controllers = ArrayList<MediaController>()

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> track(list.orEmpty()) }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = publish()
    }

    private var manager: MediaSessionManager? = null

    override fun onListenerConnected() {
        val sessions = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
        manager = sessions
        val component = ComponentName(this, NowPlayingListenerService::class.java)
        try {
            sessions.addOnActiveSessionsChangedListener(sessionsListener, component)
            track(sessions.getActiveSessions(component))
        } catch (_: SecurityException) {
            // Akses dicabut di tengah jalan.
        }
    }

    override fun onListenerDisconnected() {
        release()
        NowPlayingBridge.clear(this)
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private fun release() {
        for (controller in controllers) {
            try {
                controller.unregisterCallback(callback)
            } catch (_: Exception) {
            }
        }
        controllers.clear()
        try {
            manager?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (_: Exception) {
        }
        manager = null
    }

    private fun track(list: List<MediaController>) {
        for (controller in controllers) {
            try {
                controller.unregisterCallback(callback)
            } catch (_: Exception) {
            }
        }
        controllers.clear()
        for (controller in list) {
            try {
                controller.registerCallback(callback)
                controllers.add(controller)
            } catch (_: Exception) {
            }
        }
        publish()
    }

    // Yang sedang diputar didahulukan; kalau tidak ada, sesi pertama yang punya judul (jeda).
    private fun publish() {
        val playing = controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING && it.metadata != null
        }
        val chosen = playing ?: controllers.firstOrNull {
            !it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank()
        }
        NowPlayingBridge.write(this, chosen)
    }
}
