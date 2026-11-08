package com.israadev.nuxlauncher.ui.control

import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import com.movtery.zalithlauncher.bridge.CURSOR_DISABLED
import com.movtery.zalithlauncher.bridge.ZLBridgeStates
import org.lwjgl.glfw.CallbackBridge

/*
 * Mouse fisik di MENU game (kursor Minecraft tampil), ditangani langsung di GameActivity.
 *
 * Kenapa di sini dan bukan di Compose: log diagnosis menunjukkan Android mengirim klik mouse ke
 * GameActivity, tetapi tidak satu pun sampai ke penangan Compose di lapisan game (kursor ikut
 * bergerak, klik hilang). Pengiriman event Compose bergantung pada urutan lapisan, hit-test, dan
 * konsumsi event, jadi di sini jalurnya dipotong: event mentah dibaca sebelum Compose menyentuhnya.
 * Cara ini sama dengan yang dipakai SDL dan PojavLauncher (View/Activity, bukan Compose).
 *
 * Tombol dihitung dari buttonState setiap event (bukan hanya dari ACTION_DOWN/UP), jadi satu event
 * yang hilang tidak membuat tombol tersangkut. Hanya PERUBAHAN status yang dikirim, dan satu pelacak
 * dipakai bersama klik kanan yang datang sebagai KEYCODE_BACK, jadi tidak pernah terkirim ganda.
 *
 * Event tidak dikonsumsi di sini: Compose tetap menerimanya (posisi hover, penunjuk sistem, dan
 * penyembunyian tombol layar). Tombol dan gulir mouse di filter Compose dimatikan untuk menghindari
 * kiriman ganda (lihat physicalMouseHover).
 *
 * Murni digerakkan event: tanpa polling, tanpa thread, tanpa alokasi per event.
 */
internal object MenuMouse {
    /** Saklar cadangan. Bila dimatikan, jalur Compose lama kembali memegang tombol dan gulir. */
    @JvmField
    var enabled = true

    /**
     * Lapisan launcher (menu dalam game, dialog keluar, editor, layar loading) sedang menutupi game.
     * Diisi dari Compose. Saat true, klik mouse milik lapisan itu dan tidak diteruskan ke game.
     */
    @JvmField
    var overlayOpen = false

    private val loc = IntArray(2)
    private var syntheticDown = false
    private var backDirect = false

    // Tombol GLFW yang sedang tertekan akibat mouse fisik (bit = nomor tombol). Dipakai SEMUA jalur mouse
    // fisik (menu absolut, pointer capture, klik kanan lewat BACK) sebagai pintu terakhir ke game.
    private var heldGlfw = 0

    /**
     * Kirim tombol mouse fisik ke game. Tekan ganda atau lepas ganda dibuang, mis. klik kanan yang datang
     * dua kali (tombol BACK + event gerak), jadi game tidak pernah menerima satu klik dua kali.
     */
    fun sendButton(glfw: Int, pressed: Boolean) {
        if (glfw < 0 || glfw > 30) {
            CallbackBridge.sendMouseButton(glfw, pressed)
            return
        }
        val bit = 1 shl glfw
        if (((heldGlfw and bit) != 0) == pressed) return
        heldGlfw = if (pressed) heldGlfw or bit else heldGlfw and bit.inv()
        CallbackBridge.sendMouseButton(glfw, pressed)
    }

    private fun releaseHeld() {
        var glfw = 0
        while (heldGlfw != 0 && glfw <= 30) {
            if ((heldGlfw and (1 shl glfw)) != 0) sendButton(glfw, false)
            glfw++
        }
        heldGlfw = 0
    }

    private val buttons = CapturedMouseButtons { button, pressed ->
        val glfw = MouseButtons.toGlfw(button)
        if (glfw != null) {
            MouseDiag.buttonsSent++
            sendButton(glfw, pressed)
        } else {
            MouseDiag.buttonsDropped++
        }
    }

    /** Kursor menu game tampil dan tidak ada lapisan launcher di atasnya. */
    private fun menuActive(): Boolean =
        enabled && !overlayOpen && ZLBridgeStates.cursorMode.value != CURSOR_DISABLED

    /**
     * Event absolut (hover/sentuh) ditangani di sini hanya bila mouse tidak ditangkap. Saat mouse launcher
     * aktif (capture dipegang) event itu tidak datang lagi, dan bila ada yang nyasar, posisi absolutnya akan
     * melompatkan kursor launcher.
     */
    private fun canHandle(): Boolean = menuActive() && !LauncherMouse.captureHeld.value

    /**
     * Tombol dari jalur tangkap (mouse launcher di menu). Memakai pelacak yang sama dengan klik kanan yang
     * datang sebagai KEYCODE_BACK, jadi satu klik tidak pernah terkirim dua kali.
     */
    fun setButton(button: Int, pressed: Boolean) {
        if (pressed && LauncherMouse.captureHeld.value) LauncherMouse.syncCursor?.invoke()
        buttons.set(button, pressed)
    }

    private fun resetMenu() {
        syntheticDown = false
        buttons.reset()
    }

    /** Lepaskan semua tombol mouse fisik yang masih tertahan (sekali per tombol), dari jalur mana pun. */
    fun reset() {
        resetMenu()
        releaseHeld()
    }

    /**
     * Event sentuh dari mouse: ACTION_DOWN / MOVE (saat tombol ditahan) / UP. Dipanggil dari
     * GameActivity.dispatchTouchEvent sebelum Compose. [content] dipakai untuk mengubah koordinat
     * jendela menjadi koordinat lapisan game.
     */
    fun onTouch(event: MotionEvent, content: View?) {
        if (!event.isFromSource(InputDevice.SOURCE_MOUSE)) return
        if (!canHandle()) {
            MouseDiag.actMenuBlocked++
            resetMenu()
            return
        }
        MouseDiag.actMenu++

        val action = event.actionMasked
        if (action == MotionEvent.ACTION_CANCEL) {
            resetMenu()
            return
        }

        // Posisi dulu: klik harus jatuh di titik penunjuk, bukan di posisi hover terakhir yang sempat sampai.
        sendPosition(event.x, event.y, content)

        var state = event.buttonState
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                // Sebagian touchpad melaporkan ketukan sebagai DOWN tanpa tombol: anggap klik kiri.
                syntheticDown = state == 0
                if (syntheticDown) state = MotionEvent.BUTTON_PRIMARY
            }

            MotionEvent.ACTION_MOVE -> {
                if (syntheticDown && state == 0) state = MotionEvent.BUTTON_PRIMARY
            }

            // Mouse satu penunjuk: UP berarti tombol terakhir dilepas.
            MotionEvent.ACTION_UP -> {
                syntheticDown = false
                state = 0
            }
        }
        buttons.onSnapshot(state)
    }

    /**
     * Event generik dari mouse: hover, ACTION_BUTTON_PRESS/RELEASE, dan roda gulir. Dipanggil dari
     * GameActivity.dispatchGenericMotionEvent sebelum Compose.
     */
    fun onGeneric(event: MotionEvent) {
        if (!event.isFromSource(InputDevice.SOURCE_MOUSE)) return
        if (!canHandle()) {
            MouseDiag.actMenuBlocked++
            resetMenu()
            return
        }
        MouseDiag.actMenu++

        when (event.actionMasked) {
            MotionEvent.ACTION_BUTTON_PRESS,
            MotionEvent.ACTION_BUTTON_RELEASE ->
                buttons.onEvent(event.actionMasked, event.actionButton, event.buttonState)

            // Hover berarti tidak ada tombol yang ditahan: sekaligus melepas tombol yang tersangkut.
            MotionEvent.ACTION_HOVER_ENTER,
            MotionEvent.ACTION_HOVER_MOVE,
            MotionEvent.ACTION_HOVER_EXIT -> buttons.onSnapshot(event.buttonState)

            MotionEvent.ACTION_SCROLL -> {
                MouseDiag.scrollSent++
                CallbackBridge.sendScroll(
                    event.getAxisValue(MotionEvent.AXIS_HSCROLL).toDouble(),
                    event.getAxisValue(MotionEvent.AXIS_VSCROLL).toDouble()
                )
            }
        }
    }

    /**
     * Klik kanan mouse yang datang sebagai KEYCODE_BACK. Di menu, tombolnya lewat pelacak yang sama
     * dengan event gerak (yang juga membawa BUTTON_SECONDARY), jadi tidak terkirim dua kali.
     * Mengembalikan true bila sudah ditangani di sini; false berarti pemanggil mengirim langsung
     * (mode bermain / lapisan launcher terbuka).
     */
    fun onBackKey(down: Boolean): Boolean {
        MouseDiag.actBack++
        if (down) {
            if (!menuActive()) {
                backDirect = true
                return false
            }
            backDirect = false
            setButton(MotionEvent.BUTTON_SECONDARY, true)
            return true
        }
        if (backDirect) {
            // Tekannya dikirim langsung, jadi lepasnya juga dikirim langsung.
            backDirect = false
            return false
        }
        buttons.set(MotionEvent.BUTTON_SECONDARY, false)
        return true
    }

    private fun sendPosition(rawX: Float, rawY: Float, content: View?) {
        val winW = CallbackBridge.windowWidth
        val winH = CallbackBridge.windowHeight
        var x = rawX
        var y = rawY
        var w = 0
        var h = 0
        if (content != null) {
            content.getLocationInWindow(loc)
            x -= loc[0]
            y -= loc[1]
            w = content.width
            h = content.height
        }
        if (w > 0 && h > 0 && winW > 0 && winH > 0) {
            x = x.coerceIn(0f, w.toFloat()) * (winW.toFloat() / w)
            y = y.coerceIn(0f, h.toFloat()) * (winH.toFloat() / h)
        }
        MouseDiag.cursorSent++
        CallbackBridge.sendCursorPos(x, y)
    }
}
