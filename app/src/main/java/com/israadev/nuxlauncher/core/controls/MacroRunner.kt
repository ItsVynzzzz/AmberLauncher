package com.israadev.nuxlauncher.core.controls

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.movtery.inputmap.keycodes.LwjglGlfwKeycode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Jembatan ke input game. Dipisah supaya pemutar makro tidak bergantung pada kelas Android/game. */
interface MacroIO {
    fun key(code: Int, down: Boolean)
    fun mouse(button: Int, down: Boolean)

    /** [direction] positif = gulir naik, negatif = turun. */
    fun scroll(direction: Int)
    fun char(c: Char)
}

/**
 * Pemutar makro "Urutan Aksi". Aturan keselamatan:
 *  - Tombol yang sedang ditahan (langkah DOWN/MDOWN/HOLD, atau tekan yang belum dilepas) dicatat dan
 *    SELALU dilepas saat makro selesai, dihentikan, atau dibatalkan. Tidak ada tombol yang tersangkut.
 *  - Langkah DOWN pada tombol yang sudah ditahan diabaikan, jadi putaran berikutnya tidak menekan ulang.
 *  - Setiap langkah diikuti jeda kecil dan tiap putaran punya jeda minimal, jadi urutan tanpa jeda
 *    tidak membanjiri game.
 */
object MacroRunner {
    /** Lama tekan-lepas untuk langkah Tekan/Klik. */
    const val TAP_MS = 35L

    /** Jeda kecil antar langkah. */
    const val STEP_GAP_MS = 10L

    /** Jeda minimal antar putaran (juga mencegah putaran tanpa jeda memakan CPU). */
    const val MIN_LOOP_MS = 30L

    /** Waktu [ms] ditambah/dikurangi acak sampai [percent] persen; hasil minimal 1 ms. */
    fun jitter(ms: Long, percent: Int): Long {
        if (percent <= 0 || ms <= 1L) return ms
        val spread = percent.coerceAtMost(MacroScript.MAX_JITTER) / 100f
        val factor = 1f + (Random.nextFloat() * 2f - 1f) * spread
        return (ms * factor).toLong().coerceAtLeast(1L)
    }

    /**
     * Menjalankan [steps]. [repeat] <= 0 berarti diulang tanpa batas (sampai dibatalkan).
     * [loopDelayMs] = jeda antar putaran. Fungsi ini kembali setelah selesai atau melempar
     * CancellationException bila dibatalkan; tombol yang tertahan dilepas di kedua kasus.
     */
    suspend fun run(
        io: MacroIO,
        steps: List<MacroStep>,
        repeat: Int,
        loopDelayMs: Long,
        jitterPercent: Int
    ) {
        if (steps.isEmpty()) return
        val heldKeys = HashSet<Int>()
        val heldMouse = HashSet<Int>()
        try {
            var round = 0
            while (repeat <= 0 || round < repeat) {
                for (step in steps) {
                    exec(io, step, heldKeys, heldMouse, jitterPercent)
                    delay(jitter(STEP_GAP_MS, jitterPercent))
                }
                round++
                if (repeat <= 0 || round < repeat) {
                    delay(jitter(maxOf(loopDelayMs, MIN_LOOP_MS), jitterPercent))
                }
            }
        } finally {
            for (k in heldKeys) io.key(k, false)
            for (m in heldMouse) io.mouse(m, false)
        }
    }

    private suspend fun exec(
        io: MacroIO,
        step: MacroStep,
        heldKeys: MutableSet<Int>,
        heldMouse: MutableSet<Int>,
        jp: Int
    ) {
        when (step) {
            is MacroStep.Tap -> pressKey(io, step.key, TAP_MS, heldKeys, jp)
            is MacroStep.KeyHold -> pressKey(io, step.key, step.ms.toLong(), heldKeys, jp)
            is MacroStep.KeyDown -> {
                if (heldKeys.add(step.key)) io.key(step.key, true)
            }
            is MacroStep.KeyUp -> {
                heldKeys.remove(step.key)
                io.key(step.key, false)
            }
            is MacroStep.Click -> pressMouse(io, step.button, TAP_MS, heldMouse, jp)
            is MacroStep.MouseHold -> pressMouse(io, step.button, step.ms.toLong(), heldMouse, jp)
            is MacroStep.MouseDown -> {
                if (heldMouse.add(step.button)) io.mouse(step.button, true)
            }
            is MacroStep.MouseUp -> {
                heldMouse.remove(step.button)
                io.mouse(step.button, false)
            }
            is MacroStep.Scroll -> io.scroll(step.direction)
            is MacroStep.Wait -> delay(jitter(step.ms.toLong(), jp))
            is MacroStep.Type -> typeChat(io, step.text, heldKeys, jp)
        }
    }

    private suspend fun pressKey(io: MacroIO, key: Int, ms: Long, held: MutableSet<Int>, jp: Int) {
        held.add(key)
        io.key(key, true)
        delay(jitter(ms, jp))
        io.key(key, false)
        held.remove(key)
    }

    private suspend fun pressMouse(io: MacroIO, button: Int, ms: Long, held: MutableSet<Int>, jp: Int) {
        held.add(button)
        io.mouse(button, true)
        delay(jitter(ms, jp))
        io.mouse(button, false)
        held.remove(button)
    }

    /**
     * Membuka chat (garis miring untuk perintah, T untuk chat biasa), mengetik, lalu Enter. Urutan dan
     * jedanya sama dengan makro "Chat/Perintah".
     */
    private suspend fun typeChat(io: MacroIO, text: String, held: MutableSet<Int>, jp: Int) {
        val cmd = text.trim()
        if (cmd.isEmpty()) return
        val isCommand = cmd.startsWith("/")
        val opener = if (isCommand) LwjglGlfwKeycode.GLFW_KEY_SLASH else LwjglGlfwKeycode.GLFW_KEY_T
        pressKey(io, opener, 20L, held, jp)
        delay(50)
        val body = if (isCommand) cmd.substring(1) else cmd
        for (ch in body) {
            io.char(ch)
            delay(10)
        }
        delay(40)
        pressKey(io, LwjglGlfwKeycode.GLFW_KEY_ENTER, 25L, held, jp)
    }
}

/**
 * Daftar makro yang sedang berjalan, satu per tombol. Dijalankan di wadah korutin milik pemanggil
 * (bukan di komposisi tombol) supaya makro yang membuka chat tidak terhenti ketika tombolnya hilang
 * karena mode layar berganti. Hanya diakses dari thread utama.
 *
 * [runningIds] adalah state Compose: tombol menggambar dirinya "ditekan" selama makronya jalan,
 * walau tombolnya sempat dihapus dan disusun ulang.
 */
object MacroRegistry {
    private val jobs = HashMap<String, Job>()

    var runningIds: Set<String> by mutableStateOf(emptySet())
        private set

    fun isRunning(id: String): Boolean = id in runningIds

    /** Menjalankan [block] untuk tombol [id]. Makro lama tombol yang sama dihentikan dulu. */
    @Synchronized
    fun start(id: String, scope: CoroutineScope, block: suspend () -> Unit) {
        stop(id)
        val job = scope.launch { block() }
        jobs[id] = job
        runningIds = runningIds + id
        job.invokeOnCompletion { finished(id, job) }
    }

    @Synchronized
    fun stop(id: String) {
        jobs.remove(id)?.cancel()
        if (id in runningIds) runningIds = runningIds - id
    }

    @Synchronized
    fun stopAll() {
        for (job in jobs.values) job.cancel()
        jobs.clear()
        if (runningIds.isNotEmpty()) runningIds = emptySet()
    }

    @Synchronized
    private fun finished(id: String, job: Job) {
        // Makro baru untuk tombol yang sama bisa sudah menggantikan yang ini; jangan hapus miliknya.
        if (jobs[id] === job) {
            jobs.remove(id)
            runningIds = runningIds - id
        }
    }
}
