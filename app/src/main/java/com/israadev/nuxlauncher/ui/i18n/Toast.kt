package com.israadev.nuxlauncher.ui.i18n

import android.content.Context

/**
 * Pengganti `android.widget.Toast` yang menerjemahkan pesan ke bahasa pilihan. Cukup mengganti
 * import-nya; cara memanggilnya tidak berubah.
 */
object Toast {
    const val LENGTH_SHORT = android.widget.Toast.LENGTH_SHORT
    const val LENGTH_LONG = android.widget.Toast.LENGTH_LONG

    fun makeText(context: Context, text: CharSequence?, duration: Int): android.widget.Toast {
        val shown: CharSequence? = if (text == null) null else Tr.t(text.toString())
        return android.widget.Toast.makeText(context, shown, duration)
    }

    fun makeText(context: Context, resId: Int, duration: Int): android.widget.Toast =
        android.widget.Toast.makeText(context, resId, duration)
}
