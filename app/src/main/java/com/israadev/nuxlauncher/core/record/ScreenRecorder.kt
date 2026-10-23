package com.israadev.nuxlauncher.core.record

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.SystemClock
import android.provider.MediaStore
import android.os.StatFs
import androidx.core.content.ContextCompat
import com.israadev.nuxlauncher.core.models.LauncherSettings
import com.israadev.nuxlauncher.ui.i18n.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Perekam layar bawaan, dirancang supaya FPS game tidak ikut turun:
 *
 *  - Seluruh jalur video memakai encoder H.264 perangkat keras lewat [MediaRecorder] dengan input
 *    Surface. Tidak ada satu pun frame yang disalin atau diproses oleh CPU/Java; yang menggambar
 *    ke encoder adalah compositor sistem (SurfaceFlinger), bukan game.
 *  - Tidak ada thread, timer, atau callback per-frame di sini. Satu-satunya pekerjaan terus-menerus
 *    adalah listener suhu bawaan sistem, yang hanya terpanggil saat status panas berubah.
 *  - Resolusi dan FPS rekaman dibatasi preset (bawaan 720p/30 fps) lalu disesuaikan dengan
 *    kemampuan encoder HP, karena beban rekam naik hampir sebanding dengan jumlah piksel x FPS.
 *  - Bila HP tidak punya encoder H.264 perangkat keras, rekaman ditolak (encoder perangkat lunak
 *    akan memakan CPU yang dibutuhkan game).
 *  - Rekaman otomatis dijeda saat game keluar dari layar ([pause]/[resume]) dan dihentikan bila HP
 *    terlalu panas, ruang penyimpanan hampir habis, atau sistem mencabut izin tangkap layar.
 *  - Berhenti dikerjakan di thread terpisah (finalisasi berkas MP4 bisa memakan ratusan milidetik)
 *    kecuali saat proses game akan dimatikan ([stopBlocking]).
 *
 * Semua pemanggilan publik dilakukan dari thread utama. Objek ini hidup di proses `:game`, sama
 * dengan GameActivity dan [RecordService].
 */
object ScreenRecorder {

    /**
     * Status yang dibaca UI. Semua waktu memakai [SystemClock.elapsedRealtime]. Lama rekaman yang
     * tampil tidak menghitung waktu jeda: [elapsedBeforeMs] adalah lama dari bagian-bagian
     * sebelumnya, [segmentStart] awal bagian yang sedang berjalan (dipakai hanya bila tidak dijeda).
     */
    data class State(
        val recording: Boolean = false,
        val paused: Boolean = false,
        val startedAt: Long = 0L,
        val elapsedBeforeMs: Long = 0L,
        val segmentStart: Long = 0L
    ) {
        /** Lama rekaman (ms) pada saat [now], tanpa waktu jeda. */
        fun elapsedMs(now: Long): Long =
            if (!recording) 0L
            else elapsedBeforeMs + if (paused) 0L else (now - segmentStart).coerceAtLeast(0L)
    }

    /** Alasan rekaman ditahan; rekaman jalan lagi hanya bila semua alasan sudah dilepas. */
    enum class Hold {
        /** Layar game tidak sedang tampil (Home, aplikasi lain, layar mati). */
        SCREEN,

        /** Menu samping game terbuka: menunya tidak perlu ikut terekam. */
        MENU
    }

    /** Bahan baku konfigurasi video yang sudah dihitung. */
    data class VideoPlan(val width: Int, val height: Int, val fps: Int, val bitrate: Int)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val main = Handler(Looper.getMainLooper())

    private var appContext: Context? = null
    private var projection: MediaProjection? = null
    private var recorder: MediaRecorder? = null
    private var display: VirtualDisplay? = null
    private var output: Output? = null
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
    private var pendingFinalize: Thread? = null

    private class Output(
        val uri: Uri?,
        val file: File?,
        val pfd: ParcelFileDescriptor?,
        val displayName: String
    )

    val isRecording: Boolean get() = _state.value.recording

    // ---------------------------------------------------------------------------------------
    // Rencana video
    // ---------------------------------------------------------------------------------------

    /** Sisi pendek maksimum (piksel) untuk tiap preset kualitas. */
    private fun presetShortSide(quality: String): Int = when (quality) {
        "eco" -> 540
        "high" -> 1080
        else -> 720
    }

    private fun isSoftwareCodec(info: MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return info.isSoftwareOnly
        val n = info.name.lowercase(Locale.US)
        return n.startsWith("omx.google.") || n.startsWith("c2.android.") || n.contains(".sw.")
    }

    /**
     * Kemampuan encoder H.264 perangkat keras pertama. Null berarti pencarian gagal (jangan
     * menolak rekaman). Melempar [NoHardwareEncoder] bila daftar encoder terbaca tapi tidak ada
     * satu pun yang perangkat keras.
     */
    private fun hardwareAvcCaps(): MediaCodecInfo.VideoCapabilities? {
        val infos = try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        } catch (_: Throwable) {
            return null
        }
        val hw = infos.firstOrNull { info ->
            info.isEncoder &&
                info.supportedTypes.any { it.equals("video/avc", ignoreCase = true) } &&
                !isSoftwareCodec(info)
        } ?: throw NoHardwareEncoder()
        return try {
            hw.getCapabilitiesForType("video/avc").videoCapabilities
        } catch (_: Throwable) {
            null
        }
    }

    private class NoHardwareEncoder : RuntimeException()

    private fun alignDown(value: Int, alignment: Int): Int {
        val a = max(alignment, 2)
        return max(a, value / a * a)
    }

    /**
     * Menghitung ukuran, FPS, dan bitrate. [srcW] x [srcH] adalah ukuran layar (bentuk landscape).
     * Tidak pernah memperbesar dari ukuran layar asli.
     */
    fun plan(srcW: Int, srcH: Int, settings: LauncherSettings): VideoPlan {
        val caps = hardwareAvcCaps()

        val srcLong = max(srcW, srcH).coerceAtLeast(2)
        val srcShort = min(srcW, srcH).coerceAtLeast(2)
        val scale = min(1f, presetShortSide(settings.recordQuality) / srcShort.toFloat())
        var w = (srcLong * scale).roundToInt()
        var h = (srcShort * scale).roundToInt()

        val wAlign = caps?.widthAlignment ?: 2
        val hAlign = caps?.heightAlignment ?: 2
        w = alignDown(w, wAlign)
        h = alignDown(h, hAlign)

        // Kecilkan bertahap bila encoder menolak ukuran ini (sangat jarang pada ukuran 16:9 biasa).
        if (caps != null) {
            var guard = 0
            while (!caps.isSizeSupported(w, h) && guard < 8) {
                w = alignDown((w * 0.85f).roundToInt(), wAlign)
                h = alignDown((h * 0.85f).roundToInt(), hAlign)
                guard++
            }
        }

        var fps = if (settings.recordFps >= 60) 60 else 30
        if (caps != null) {
            val upper = try {
                caps.getSupportedFrameRatesFor(w, h).upper.toInt()
            } catch (_: Throwable) {
                fps
            }
            if (upper in 1 until fps) fps = if (upper >= 30) 30 else upper
        }

        // ~0,10 bit per piksel per frame: cukup bersih untuk gambar Minecraft yang bergerak
        // banyak tanpa membuat encoder dan penyimpanan bekerja berlebihan.
        var bitrate = (w.toLong() * h * fps * 0.10).toLong()
            .coerceIn(1_500_000L, 16_000_000L).toInt()
        if (caps != null) {
            try {
                bitrate = caps.bitrateRange.clamp(bitrate)
            } catch (_: Throwable) {
            }
        }
        return VideoPlan(w, h, fps, bitrate)
    }

    // ---------------------------------------------------------------------------------------
    // Mulai
    // ---------------------------------------------------------------------------------------

    /**
     * Dipanggil dari [RecordService] SETELAH startForeground (wajib di Android 14+). Mengembalikan
     * true bila rekaman berjalan; bila gagal semua sumber daya sudah dilepas dan pesan sudah tampil.
     */
    fun start(
        context: Context,
        resultCode: Int,
        data: Intent,
        screenW: Int,
        screenH: Int,
        screenDpi: Int,
        settings: LauncherSettings,
        useMic: Boolean
    ): Boolean {
        if (_state.value.recording) return true
        val ctx = context.applicationContext
        appContext = ctx

        val plan = try {
            plan(screenW, screenH, settings)
        } catch (_: NoHardwareEncoder) {
            toast("HP ini tidak punya encoder video perangkat keras. Rekaman dibatalkan supaya game tidak lag.")
            return false
        } catch (e: Throwable) {
            toast("Gagal menyiapkan rekaman: ${e.message ?: e.javaClass.simpleName}")
            return false
        }

        val maxBytes = freeSpaceBudget(ctx)
        if (maxBytes <= 0L) {
            toast("Ruang penyimpanan kurang untuk merekam.")
            return false
        }

        val mpm = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (mpm == null) {
            toast("Perekam layar tidak tersedia di HP ini.")
            return false
        }

        var rec: MediaRecorder? = null
        var proj: MediaProjection? = null
        var vd: VirtualDisplay? = null
        var out: Output? = null
        try {
            val o = openOutput(ctx)
            out = o

            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(ctx)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            rec = r
            // Urutan pemanggilan MediaRecorder penting; jangan diubah.
            if (useMic) r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            val fd = o.pfd
            if (fd != null) r.setOutputFile(fd.fileDescriptor)
            else r.setOutputFile(o.file!!.absolutePath)
            r.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            r.setVideoSize(plan.width, plan.height)
            r.setVideoFrameRate(plan.fps)
            r.setVideoEncodingBitRate(plan.bitrate)
            if (useMic) {
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                r.setAudioEncodingBitRate(96_000)
                r.setAudioSamplingRate(44_100)
            }
            r.setMaxFileSize(maxBytes)
            r.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED ||
                    what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED
                ) {
                    main.post {
                        toast("Ruang penyimpanan penuh. Rekaman disimpan dan dihentikan.")
                        stop()
                    }
                }
            }
            r.setOnErrorListener { _, _, _ ->
                main.post {
                    toast("Perekam mengalami kesalahan. Rekaman dihentikan.")
                    stop()
                }
            }
            r.prepare()

            val p = mpm.getMediaProjection(resultCode, data)
                ?: throw IllegalStateException("izin tangkap layar tidak valid")
            proj = p
            // Android 14+ mewajibkan callback terpasang sebelum VirtualDisplay dibuat.
            p.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    main.post {
                        if (_state.value.recording) {
                            toast("Tangkap layar dihentikan oleh sistem. Rekaman disimpan.")
                            stop()
                        }
                    }
                }
            }, main)

            vd = p.createVirtualDisplay(
                "AmberRecord",
                plan.width,
                plan.height,
                screenDpi.coerceAtLeast(120),
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                r.surface,
                null,
                null
            )
            r.start()
        } catch (e: Throwable) {
            try { vd?.release() } catch (_: Throwable) {}
            try { proj?.stop() } catch (_: Throwable) {}
            try { rec?.reset() } catch (_: Throwable) {}
            try { rec?.release() } catch (_: Throwable) {}
            if (out != null) discardOutput(ctx, out)
            toast("Gagal memulai rekaman: ${e.message ?: e.javaClass.simpleName}")
            return false
        }

        recorder = rec
        projection = proj
        display = vd
        output = out
        val now = SystemClock.elapsedRealtime()
        _state.value = State(recording = true, paused = false, startedAt = now, elapsedBeforeMs = 0L, segmentStart = now)
        registerThermalGuard(ctx, settings)
        // Tidak ada toast saat mulai: toast ikut terekam di awal video. Tandanya cukup getar singkat
        // (dari GameActivity), notifikasi, dan penghitung waktu di menu.
        applyHolds()
        return true
    }

    // ---------------------------------------------------------------------------------------
    // Jeda / lanjut. Alasan jeda dicatat terpisah ([Hold]) dan dipertahankan walau belum merekam,
    // jadi rekaman yang baru dimulai langsung mengikuti keadaan layar saat itu.
    // ---------------------------------------------------------------------------------------

    private val holds = HashSet<Hold>()
    private val pendingRelease = HashMap<Hold, Runnable>()

    fun hold(reason: Hold) {
        pendingRelease.remove(reason)?.let { main.removeCallbacks(it) }
        holds.add(reason)
        applyHolds()
    }

    /** [delayMs] > 0 menunda pelepasan, mis. supaya animasi menutup menu tidak ikut terekam. */
    fun release(reason: Hold, delayMs: Long = 0L) {
        pendingRelease.remove(reason)?.let { main.removeCallbacks(it) }
        if (delayMs <= 0L) {
            holds.remove(reason)
            applyHolds()
            return
        }
        val r = Runnable {
            pendingRelease.remove(reason)
            holds.remove(reason)
            applyHolds()
        }
        pendingRelease[reason] = r
        main.postDelayed(r, delayMs)
    }

    private fun applyHolds() {
        if (holds.isEmpty()) resumeRecording() else pauseRecording()
    }

    private fun pauseRecording() {
        val s = _state.value
        if (!s.recording || s.paused) return
        try {
            recorder?.pause()
            val now = SystemClock.elapsedRealtime()
            _state.value = s.copy(paused = true, elapsedBeforeMs = s.elapsedMs(now))
        } catch (_: Throwable) {
        }
    }

    private fun resumeRecording() {
        val s = _state.value
        if (!s.recording || !s.paused) return
        try {
            recorder?.resume()
            _state.value = s.copy(paused = false, segmentStart = SystemClock.elapsedRealtime())
        } catch (_: Throwable) {
        }
    }

    // ---------------------------------------------------------------------------------------
    // Berhenti
    // ---------------------------------------------------------------------------------------

    /** Berhenti tanpa menahan thread utama; berkas diselesaikan di thread latar. */
    fun stop() = stopInternal(blocking = false)

    /** Berhenti dan menunggu berkas selesai ditulis. Hanya untuk saat proses game akan dimatikan. */
    fun stopBlocking() = stopInternal(blocking = true)

    private fun stopInternal(blocking: Boolean) {
        val rec = recorder
        val ctx = appContext
        val out = output
        if (!_state.value.recording || rec == null || ctx == null || out == null) {
            // Tunggu finalisasi sebelumnya (mis. dari stop() tak lama sebelum proses dimatikan).
            if (blocking) try { pendingFinalize?.join(3000) } catch (_: Throwable) {}
            RecordService.requestStop(appContext)
            return
        }

        val proj = projection
        val vd = display
        recorder = null
        projection = null
        display = null
        output = null
        unregisterThermalGuard(ctx)
        _state.value = State()

        // Hentikan sumber frame lebih dulu supaya beban komposisi langsung hilang.
        try { vd?.release() } catch (_: Throwable) {}
        try { proj?.stop() } catch (_: Throwable) {}
        RecordService.requestStop(ctx)

        val finalize = Runnable { finalizeOutput(ctx, rec, out) }
        if (blocking) {
            finalize.run()
        } else {
            val t = thread(name = "amber-rec-finalize", isDaemon = false, priority = Thread.MIN_PRIORITY) {
                finalize.run()
            }
            pendingFinalize = t
        }
    }

    private fun finalizeOutput(ctx: Context, rec: MediaRecorder, out: Output) {
        var ok = true
        try {
            rec.stop()
        } catch (_: Throwable) {
            // Terjadi bila berhenti sebelum ada frame sama sekali: berkasnya tidak valid.
            ok = false
        }
        try { rec.reset() } catch (_: Throwable) {}
        try { rec.release() } catch (_: Throwable) {}
        try { out.pfd?.close() } catch (_: Throwable) {}

        if (!ok) {
            discardOutput(ctx, out)
            toast("Rekaman terlalu singkat, tidak disimpan.")
            return
        }
        if (out.uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                ctx.contentResolver.update(out.uri, values, null, null)
            } catch (_: Throwable) {
            }
            toast("Rekaman disimpan di Movies/AmberLauncher")
        } else if (out.file != null) {
            try {
                MediaScannerConnection.scanFile(ctx, arrayOf(out.file.absolutePath), arrayOf("video/mp4"), null)
            } catch (_: Throwable) {
            }
            toast("Rekaman disimpan: ${out.file.name}")
        }
    }

    // ---------------------------------------------------------------------------------------
    // Penyimpanan
    // ---------------------------------------------------------------------------------------

    private const val MIN_FREE_BYTES = 300L * 1024 * 1024
    private const val MAX_FILE_BYTES = 8L * 1024 * 1024 * 1024

    /** Batas ukuran berkas = sisa ruang dikurangi cadangan 300 MB; <= 0 bila ruang tidak cukup. */
    private fun freeSpaceBudget(ctx: Context): Long {
        return try {
            val path = (ctx.getExternalFilesDir(null) ?: ctx.filesDir).path
            val free = StatFs(path).availableBytes
            min(free - MIN_FREE_BYTES, MAX_FILE_BYTES)
        } catch (_: Throwable) {
            MAX_FILE_BYTES
        }
    }

    private fun newFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "Amber_$stamp.mp4"
    }

    private fun openOutput(ctx: Context): Output {
        val name = newFileName()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Lewat MediaStore: berkas muncul di Galeri tanpa izin penyimpanan, dan MediaRecorder
            // menulis langsung ke file descriptor (tanpa salinan tambahan setelah rekaman).
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/AmberLauncher")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                values
            ) ?: throw IllegalStateException("tidak bisa membuat berkas di Movies")
            // "rw": penulis MP4 perlu berpindah posisi untuk menulis indeks di akhir berkas.
            val pfd = resolver.openFileDescriptor(uri, "rw")
            if (pfd == null) {
                try { resolver.delete(uri, null, null) } catch (_: Throwable) {}
                throw IllegalStateException("tidak bisa membuka berkas rekaman")
            }
            return Output(uri, null, pfd, name)
        }

        @Suppress("DEPRECATION")
        val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "AmberLauncher")
        val canWritePublic = ContextCompat.checkSelfPermission(
            ctx, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val dir = if (canWritePublic && (publicDir.isDirectory || publicDir.mkdirs())) {
            publicDir
        } else {
            File(ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: ctx.filesDir, "AmberLauncher").also { it.mkdirs() }
        }
        return Output(null, File(dir, name), null, name)
    }

    private fun discardOutput(ctx: Context, out: Output) {
        try { out.pfd?.close() } catch (_: Throwable) {}
        try {
            if (out.uri != null) ctx.contentResolver.delete(out.uri, null, null)
            else out.file?.delete()
        } catch (_: Throwable) {
        }
    }

    // ---------------------------------------------------------------------------------------
    // Pelindung suhu
    // ---------------------------------------------------------------------------------------

    private fun registerThermalGuard(ctx: Context, settings: LauncherSettings) {
        if (!settings.recordThermalGuard || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        try {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            val listener = PowerManager.OnThermalStatusChangedListener { status ->
                if (status >= PowerManager.THERMAL_STATUS_SEVERE && _state.value.recording) {
                    toast("HP terlalu panas. Rekaman disimpan dan dihentikan agar game tetap lancar.")
                    stop()
                }
            }
            pm.addThermalStatusListener(ContextCompat.getMainExecutor(ctx), listener)
            thermalListener = listener
        } catch (_: Throwable) {
        }
    }

    private fun unregisterThermalGuard(ctx: Context) {
        val listener = thermalListener ?: return
        thermalListener = null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        try {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            pm.removeThermalStatusListener(listener)
        } catch (_: Throwable) {
        }
    }

    // ---------------------------------------------------------------------------------------

    private fun toast(message: String) {
        val ctx = appContext ?: return
        val show = Runnable {
            try {
                Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
            } catch (_: Throwable) {
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) show.run() else main.post(show)
    }
}
