package com.israadev.nuxlauncher.core.controls

import android.content.Context
import dalvik.system.DexFile

/**
 * Mencari kelas pustaka luar berdasarkan nama sederhananya di dalam APK sendiri. Hanya dipakai
 * sebagai cadangan bila nama kelas bawaan tidak cocok (lihat [TouchControllerBridge]); jalur
 * normal tidak pernah memanggilnya.
 */
@Suppress("DEPRECATION")
object DexClassScanner {

    fun find(context: Context, simpleName: String, packagePrefix: String = "top.fifthlight."): Class<*>? {
        return try {
            val dex = DexFile(context.packageCodePath)
            try {
                val entries = dex.entries()
                while (entries.hasMoreElements()) {
                    val name = entries.nextElement()
                    if (name.startsWith(packagePrefix) && name.endsWith(".$simpleName")) {
                        return Class.forName(name, false, context.classLoader)
                    }
                }
                null
            } finally {
                try { dex.close() } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {
            null
        }
    }
}
