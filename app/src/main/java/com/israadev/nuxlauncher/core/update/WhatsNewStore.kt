package com.israadev.nuxlauncher.core.update

import android.content.Context

/**
 * Catatan "Yang baru" untuk versi yang terpasang: dibaca dari assets/whatsnew.json (dibuat saat build oleh
 * scripts/release-notes.py) dan ingatan versi mana yang sudah ditampilkan otomatis.
 *
 * Build tanpa berkas itu (mis. dibangun di komputer sendiri) tidak punya catatan: layar Tentang menampilkan
 * keterangan kosong dan dialog setelah pembaruan tidak muncul.
 */
object WhatsNewStore {
    private const val PREFS_NAME = "amber_launcher_prefs"
    private const val KEY_SEEN = "whatsnew_seen_version"

    @Volatile
    private var loaded = false

    @Volatile
    private var cached: WhatsNew.Notes? = null

    /** Catatan versi yang terpasang, atau null bila build ini tidak membawanya. Dibaca sekali lalu disimpan. */
    fun bundled(context: Context): WhatsNew.Notes? {
        if (loaded) return cached
        synchronized(this) {
            if (!loaded) {
                cached = try {
                    context.applicationContext.assets.open(WhatsNew.ASSET).bufferedReader(Charsets.UTF_8).use {
                        WhatsNew.fromJson(it.readText())
                    }
                } catch (_: Throwable) {
                    null
                }
                loaded = true
            }
        }
        return cached
    }

    /**
     * Catatan yang perlu muncul otomatis sekali setelah pembaruan, atau null. Tidak muncul pada pemasangan baru
     * (belum ada yang "baru" bagi pengguna) dan tidak pada build uji (nama versinya memuat "-uji"), yang terbit
     * tiap kali ada patch. Berat sedikit (membaca berkas), jadi panggil dari thread latar.
     */
    fun pendingAutoShow(context: Context): WhatsNew.Notes? {
        val notes = bundled(context) ?: return null
        val current = UpdateChecker.currentVersionName(context)
        if (current.isEmpty() || current.contains("-uji")) return null
        val seen = prefs(context)?.getString(KEY_SEEN, null)
        if (seen == current) return null
        if (seen == null && isFreshInstall(context)) {
            markSeen(context)
            return null
        }
        return notes
    }

    /** Menandai versi yang sedang terpasang sudah dilihat penggunanya. */
    fun markSeen(context: Context) {
        val current = UpdateChecker.currentVersionName(context)
        if (current.isEmpty()) return
        try {
            prefs(context)?.edit()?.putString(KEY_SEEN, current)?.apply()
        } catch (_: Throwable) {
        }
    }

    private fun prefs(context: Context) = try {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    } catch (_: Throwable) {
        null
    }

    private fun isFreshInstall(context: Context): Boolean = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.firstInstallTime == info.lastUpdateTime
    } catch (_: Throwable) {
        false
    }
}
