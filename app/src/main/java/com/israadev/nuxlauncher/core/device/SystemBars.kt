package com.israadev.nuxlauncher.core.device

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Bar status dan bar navigasi Android disembunyikan di seluruh launcher, dalam kondisi apa pun (juga saat
 * mouse, keyboard, atau gamepad terhubung). Bar hanya muncul sebentar bila pengguna menggesernya dari tepi
 * layar atau sistem Android sendiri yang memunculkannya, lalu hilang lagi sendiri.
 *
 * Dipakai oleh layar penuh (Activity) dan jendela dialog, karena dialog adalah jendela terpisah yang tidak
 * ikut mode layar penuh milik Activity-nya.
 */
object SystemBars {
    /** Meminta [window] menyembunyikan bar sistem. Aman dipanggil berulang; gagal diam-diam bila ditolak. */
    fun hide(window: Window) {
        try {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val attrs = window.attributes
                if (attrs.layoutInDisplayCutoutMode !=
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                ) {
                    attrs.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    window.attributes = attrs
                }
            }
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
        } catch (_: Throwable) {
            // Bar sistem tetap tampil di perangkat yang menolak; tampilan tetap berfungsi.
        }
    }
}
