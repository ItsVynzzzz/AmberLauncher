package com.israadev.nuxlauncher.core.game.input;

import com.movtery.inputmap.keycodes.LwjglGlfwKeycode;

/**
 * Mencatat tombol pengubah (Shift, Ctrl, Alt) yang sedang ditahan, dari mana pun asalnya:
 * keyboard fisik maupun tombol layar virtual.
 *
 * Minecraft versi baru membaca Shift/Ctrl dari kolom "mods" pada setiap kejadian klik dan tombol,
 * bukan dari status tombol. Tanpa catatan ini tombol Shift layar tidak pernah masuk ke "mods",
 * sehingga Shift + klik di inventory tidak memindahkan item. Kelas ini murni Java tanpa Android
 * supaya mudah diuji; semua metode aman dipanggil dari beberapa thread.
 */
public final class ModifierKeys {

    private static final int LSHIFT = 1;
    private static final int RSHIFT = 1 << 1;
    private static final int LCTRL = 1 << 2;
    private static final int RCTRL = 1 << 3;
    private static final int LALT = 1 << 4;
    private static final int RALT = 1 << 5;

    private int held;

    private static int heldBit(int keycode) {
        switch (keycode) {
            case LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT: return LSHIFT;
            case LwjglGlfwKeycode.GLFW_KEY_RIGHT_SHIFT: return RSHIFT;
            case LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL: return LCTRL;
            case LwjglGlfwKeycode.GLFW_KEY_RIGHT_CONTROL: return RCTRL;
            case LwjglGlfwKeycode.GLFW_KEY_LEFT_ALT: return LALT;
            case LwjglGlfwKeycode.GLFW_KEY_RIGHT_ALT: return RALT;
            default: return 0;
        }
    }

    private static int glfwMod(int heldBit) {
        switch (heldBit) {
            case LSHIFT:
            case RSHIFT:
                return LwjglGlfwKeycode.GLFW_MOD_SHIFT;
            case LCTRL:
            case RCTRL:
                return LwjglGlfwKeycode.GLFW_MOD_CONTROL;
            case LALT:
            case RALT:
                return LwjglGlfwKeycode.GLFW_MOD_ALT;
            default:
                return 0;
        }
    }

    /** Bit GLFW_MOD_* dari semua tombol pengubah yang sedang ditahan. */
    public synchronized int heldMods() {
        int v = 0;
        if ((held & (LSHIFT | RSHIFT)) != 0) v |= LwjglGlfwKeycode.GLFW_MOD_SHIFT;
        if ((held & (LCTRL | RCTRL)) != 0) v |= LwjglGlfwKeycode.GLFW_MOD_CONTROL;
        if ((held & (LALT | RALT)) != 0) v |= LwjglGlfwKeycode.GLFW_MOD_ALT;
        return v;
    }

    /**
     * Mencatat penekanan/pelepasan [keycode]. Untuk tombol pengubah, mengembalikan [mods] yang
     * disesuaikan seperti GLFW: kejadian tekan Shift sudah memuat bit Shift (dan pengubah lain yang
     * sedang ditahan), kejadian lepas tidak (kecuali Shift satunya masih ditahan). Tombol lain:
     * [mods] dikembalikan apa adanya.
     */
    public synchronized int onKey(int keycode, boolean down, int mods) {
        int bit = heldBit(keycode);
        if (bit == 0) return mods;
        if (down) held |= bit; else held &= ~bit;
        int modBit = glfwMod(bit);
        return (mods & ~modBit) | heldMods();
    }

    /** Lupakan semua tombol yang tercatat ditahan (mis. saat input direset). */
    public synchronized void clear() {
        held = 0;
    }
}
