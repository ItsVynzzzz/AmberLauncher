package com.israadev.nuxlauncher.core.update

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/**
 * Penyimpanan kecil untuk pembaruan: salinan daftar rilis terakhir beserta ETag-nya (supaya pengecekan
 * berikutnya cukup 304 dan bisa dijawab walau sedang offline), versi yang dilewati pengguna, dan
 * kapan pengecekan boleh diulang setelah kena batas GitHub.
 */
internal object UpdateStore {
    private const val PREFS_NAME = "amber_launcher_prefs"
    private const val KEY_SKIPPED_TAG = "update_skipped_tag"
    private const val KEY_BLOCKED_UNTIL = "update_blocked_until"
    private const val CACHE_FILE = "update_releases.json"

    class Cache(val etag: String?, val body: String)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun readCache(context: Context): Cache? {
        val file = File(context.filesDir, CACHE_FILE)
        if (!file.isFile) return null
        return try {
            val o = JsonParser.parseString(file.readText()).asJsonObject
            val body = o.get("body")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
            val etag = o.get("etag")?.takeIf { it.isJsonPrimitive }?.asString
            Cache(etag, body)
        } catch (_: Exception) {
            null
        }
    }

    fun writeCache(context: Context, etag: String?, body: String) {
        try {
            val file = File(context.filesDir, CACHE_FILE)
            val tmp = File(context.filesDir, "$CACHE_FILE.tmp")
            val json = JsonObject().apply {
                addProperty("etag", etag)
                addProperty("body", body)
            }
            tmp.writeText(json.toString())
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (_: Exception) {
        }
    }

    fun skippedTag(context: Context): String? = prefs(context).getString(KEY_SKIPPED_TAG, null)

    fun setSkippedTag(context: Context, tag: String) {
        prefs(context).edit().putString(KEY_SKIPPED_TAG, tag).apply()
    }

    fun blockedUntil(context: Context): Long = prefs(context).getLong(KEY_BLOCKED_UNTIL, 0L)

    fun setBlockedUntil(context: Context, untilMillis: Long) {
        prefs(context).edit().putLong(KEY_BLOCKED_UNTIL, untilMillis).apply()
    }
}
