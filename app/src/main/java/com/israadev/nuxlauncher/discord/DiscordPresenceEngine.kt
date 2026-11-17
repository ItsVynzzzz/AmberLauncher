package com.israadev.nuxlauncher.discord

/** Isi Rich Presence yang ditampilkan di profil Discord. [startMs] <= 0 berarti tanpa penghitung waktu. */
data class PresenceInfo(val details: String, val state: String, val startMs: Long)

/** Pemisah antara logika di bawah dan kode native, supaya logikanya bisa diuji tanpa HP. */
internal interface PresenceBackend {
    fun init(appId: Long): Boolean
    fun update(details: String, state: String, startMs: Long): Boolean
    fun clear()
    fun tick()
    fun shutdown()
    fun takeResult(): String
    fun takeLog(): String

    /** true bila SDK baru saja mencatat bahwa koneksi ke aplikasi Discord terputus. */
    fun takeDropped(): Boolean
}

/** Pemisah antara logika di bawah dan jam/thread Android. Semua dipanggil dari satu thread kerja. */
internal interface PresenceScheduler {
    fun now(): Long
    fun postDelayed(task: Runnable, delayMs: Long)
    fun cancel(task: Runnable)
    fun cancelAll()
}

/**
 * Menjaga Rich Presence tetap tampil selama pemain bermain, dan HEMAT di HP lemah.
 *
 * Yang dipelajari dari log nyata: koneksi ke aplikasi Discord sering gagal saat game baru dibuka (Discord
 * belum berjalan atau masih lambat dimulai) dan bisa putus di tengah sesi (Discord dimatikan Android saat
 * memori sempit). Presence yang hanya dikirim sekali lalu hilang. Karena itu:
 *  - Selama belum tersambung, presence dikirim ulang tiap [MIN_GAP_MS] sampai Discord menjawab ok.
 *  - Setelah tersambung, bila SDK melaporkan koneksi putus, presence dikirim ulang segera (dalam batas
 *    [MIN_GAP_MS]); sebagai jaring pengaman dikirim ulang juga tiap [REASSERT_MS].
 *  - Bila tak kunjung tersambung dalam [ATTEMPT_WINDOW_MS], klien SDK dilepas sama sekali (tidak ada thread SDK,
 *    tidak ada percobaan sambung ke Discord) dan dicoba lagi [RETRY_AFTER_MS] kemudian. Jadi Discord yang mati
 *    tidak dibangunkan terus-menerus saat game butuh memori. Presence tetap menyusul begitu Discord siap.
 *  - Waktu bermain dihitung Discord dari [PresenceInfo.startMs], jadi tidak ada pembaruan untuk itu.
 */
internal class PresenceEngine(
    private val backend: PresenceBackend,
    private val scheduler: PresenceScheduler,
    private val appId: Long,
    private val log: (String) -> Unit,
    private val warn: (String, Throwable?) -> Unit,
    private val onFinished: () -> Unit
) {
    companion object {
        const val TICK_MS = 1_000L
        const val MIN_GAP_MS = 15_000L
        const val ATTEMPT_WINDOW_MS = 180_000L
        const val RETRY_AFTER_MS = 600_000L
        const val REASSERT_MS = 300_000L
    }

    private var wanted: PresenceInfo? = null
    private var dirty = false
    private var clientActive = false
    private var settled = false
    private var attemptStart = 0L
    private var lastSentAt = -1L // -1 = belum pernah mengirim pada percobaan ini
    private var lastOkAt = 0L
    private var lastResult = ""

    // Sesudah stop() engine ini tidak dipakai lagi: pembaruan yang telanjur antre dari thread lain diabaikan
    // (thread kerjanya sedang ditutup), supaya tidak ada klien SDK yang terbuat lalu tertinggal.
    private var finished = false

    private val tickTask = Runnable { tick() }
    private val retryTask = Runnable { startAttempt("mencoba lagi") }

    /** Menampilkan [info]; null menghapus presence. */
    fun update(info: PresenceInfo?) {
        if (finished) return
        wanted = info
        dirty = true
        if (info != null && !clientActive) startAttempt("permintaan presence") else flush()
    }

    /** Menghapus presence, melepas klien SDK, dan menghentikan semua pekerjaan terjadwal. */
    fun stop() {
        finished = true
        scheduler.cancelAll()
        try {
            if (clientActive) {
                backend.shutdown()
                drain()
            }
        } catch (e: Throwable) {
            warn("Discord RPC gagal ditutup rapi: ${e.message}", e)
        }
        clientActive = false
        settled = false
        wanted = null
        dirty = false
        onFinished()
    }

    private fun startAttempt(reason: String) {
        if (wanted == null) return
        scheduler.cancel(retryTask)
        try {
            if (!backend.init(appId)) {
                drain()
                warn("Discord RPC berhenti: klien SDK gagal dibuat", null)
                stop()
                return
            }
        } catch (e: Throwable) {
            warn("Discord RPC berhenti: ${e.message}", e)
            stop()
            return
        }
        log("Klien SDK dibuat ($reason)")
        clientActive = true
        settled = false
        attemptStart = scheduler.now()
        lastSentAt = -1L
        dirty = true
        scheduler.cancel(tickTask)
        scheduler.postDelayed(tickTask, TICK_MS)
        flush()
    }

    private fun tick() {
        if (!clientActive) return
        try {
            backend.tick()
            drain()
            val now = scheduler.now()
            if (backend.takeDropped()) {
                if (settled) {
                    log("Koneksi ke Discord terputus, presence dikirim ulang")
                    settled = false
                    attemptStart = now
                }
                dirty = true
            }
            if (settled && now - lastOkAt >= REASSERT_MS) dirty = true
            if (!settled && now - attemptStart > ATTEMPT_WINDOW_MS) {
                pause()
                return
            }
            flush()
        } catch (e: Throwable) {
            warn("Discord RPC dihentikan: ${e.message}", e)
            stop()
            return
        }
        scheduler.postDelayed(tickTask, TICK_MS)
    }

    private fun flush() {
        if (!dirty || !clientActive) return
        val info = wanted
        val now = scheduler.now()
        if (info != null && lastSentAt >= 0L && now - lastSentAt < MIN_GAP_MS) return // tick berikutnya mencoba lagi
        dirty = false
        try {
            if (info == null) {
                backend.clear()
            } else {
                val sent = backend.update(fit(info.details), fit(info.state), info.startMs)
                if (!sent) warn("Presence tidak terkirim ke SDK", null)
            }
            lastSentAt = now
        } catch (e: Throwable) {
            warn("Discord RPC dihentikan: ${e.message}", e)
            stop()
        }
    }

    /** Tak kunjung tersambung: lepas klien SDK dan coba lagi nanti, tanpa pekerjaan apa pun di antaranya. */
    private fun pause() {
        log("Discord belum tersambung dalam ${ATTEMPT_WINDOW_MS / 1000} detik; SDK dilepas, coba lagi ${RETRY_AFTER_MS / 60_000} menit lagi")
        scheduler.cancel(tickTask)
        try {
            backend.shutdown()
            drain()
        } catch (e: Throwable) {
            warn("Discord RPC gagal ditutup rapi: ${e.message}", e)
        }
        clientActive = false
        settled = false
        scheduler.postDelayed(retryTask, RETRY_AFTER_MS)
    }

    /** Memindahkan hasil dan log SDK ke log launcher; hasil gagal membuat presence dikirim ulang nanti. */
    private fun drain() {
        val result = backend.takeResult()
        if (result.isNotEmpty()) {
            if (result != lastResult) log("Presence: $result")
            lastResult = result
            if (result == "ok") {
                settled = true
                lastOkAt = scheduler.now()
            } else {
                dirty = true
            }
        }
        val text = backend.takeLog()
        if (text.isNotBlank()) {
            for (line in text.lineSequence()) if (line.isNotBlank()) warn(line, null)
        }
    }

    /** Discord menerima teks 2 sampai 128 karakter. */
    private fun fit(text: String): String {
        var t = text.replace('\n', ' ').trim()
        if (t.length > 100) t = t.take(100)
        if (t.isNotEmpty() && Character.isHighSurrogate(t.last())) t = t.dropLast(1)
        return if (t.length < 2) "Minecraft" else t
    }
}
