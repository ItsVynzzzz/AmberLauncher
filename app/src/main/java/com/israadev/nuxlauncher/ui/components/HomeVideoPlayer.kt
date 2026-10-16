package com.israadev.nuxlauncher.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.PowerManager
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.delay
import java.io.File

/**
 * Mengendalikan MediaPlayer dan TextureView untuk latar video Beranda. Semua dipanggil dari
 * thread utama. Hanya memakai API bawaan Android, jadi tidak menambah ukuran APK.
 */
private class HomeVideoController(private val file: File) {
    /** true = bingkai pertama sudah tampil; false = permukaan hilang dan perlu poster lagi. */
    var onFrame: (Boolean) -> Unit = {}
    var onError: () -> Unit = {}

    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var texture: TextureView? = null
    private var container: FrameLayout? = null
    private var prepared = false
    private var rendered = false
    private var released = false
    private var failed = false
    private var wantPlay = false
    private var volume = 0f
    private var videoW = 0
    private var videoH = 0
    private val sampleBitmap: Bitmap by lazy { AmbientSampler.newBitmap() }
    private val samplePixels = AmbientSampler.newPixels()

    fun createView(ctx: Context): View {
        val frame = FrameLayout(ctx)
        val tv = TextureView(ctx)
        frame.addView(
            tv,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )
        frame.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fitCover() }
        tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                startPlayer(st)
            }

            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}

            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                stopPlayer()
                if (rendered) {
                    rendered = false
                    onFrame(false)
                }
                return true
            }

            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }
        container = frame
        texture = tv
        return frame
    }

    /** Memenuhi panel tanpa merusak rasio (potong tepi), seperti CENTER_CROP pada gambar. */
    private fun fitCover() {
        val c = container ?: return
        val tv = texture ?: return
        val cw = c.width
        val ch = c.height
        if (cw <= 0 || ch <= 0 || videoW <= 0 || videoH <= 0) return
        val scale = maxOf(cw.toFloat() / videoW, ch.toFloat() / videoH)
        val w = maxOf(cw, (videoW * scale).toInt())
        val h = maxOf(ch, (videoH * scale).toInt())
        val lp = tv.layoutParams as? FrameLayout.LayoutParams ?: return
        if (lp.width != w || lp.height != h) {
            lp.width = w
            lp.height = h
            tv.layoutParams = lp
        }
    }

    private fun startPlayer(st: SurfaceTexture) {
        if (released || failed) return
        stopPlayer()
        val s = Surface(st)
        surface = s
        prepared = false
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            mp.setDataSource(file.absolutePath)
            mp.setSurface(s)
            mp.isLooping = true
            mp.setVolume(volume, volume)
            mp.setOnVideoSizeChangedListener { _, w, h ->
                videoW = w
                videoH = h
                fitCover()
            }
            mp.setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START && !rendered) {
                    rendered = true
                    onFrame(true)
                }
                false
            }
            mp.setOnPreparedListener {
                prepared = true
                applyPlayState()
            }
            mp.setOnErrorListener { _, _, _ ->
                fail()
                true
            }
            mp.prepareAsync()
        } catch (e: Throwable) {
            fail()
        }
    }

    private fun stopPlayer() {
        val mp = player
        player = null
        prepared = false
        if (mp != null) {
            try {
                mp.setOnErrorListener(null)
                mp.setOnInfoListener(null)
                mp.setOnPreparedListener(null)
                mp.release()
            } catch (_: Throwable) {
            }
        }
        try {
            surface?.release()
        } catch (_: Throwable) {
        }
        surface = null
    }

    private fun fail() {
        if (failed) return
        failed = true
        stopPlayer()
        onError()
    }

    private fun applyPlayState() {
        val mp = player ?: return
        if (!prepared || failed) return
        try {
            if (wantPlay) {
                mp.setVolume(volume, volume)
                if (!mp.isPlaying) mp.start()
            } else if (mp.isPlaying) {
                mp.pause()
            }
        } catch (e: Throwable) {
            fail()
        }
    }

    /**
     * Putar atau jeda, dan atur volume (0..1). Bila akan mulai memutar dengan suara dan aplikasi
     * lain sedang memutar musik, video dibisukan supaya tidak menimpa musik pengguna.
     */
    fun setState(play: Boolean, vol: Float, otherMusicActive: () -> Boolean) {
        val wasPlaying = try {
            player?.isPlaying == true
        } catch (_: Throwable) {
            false
        }
        volume = if (vol > 0f && play && !wasPlaying && otherMusicActive()) 0f else vol
        wantPlay = play
        applyPlayState()
    }

    /** Warna tepi bingkai yang sedang tampil, atau null bila belum bisa dibaca. */
    fun sample(): AmbientColors? {
        val tv = texture ?: return null
        if (player == null || !prepared || !rendered || !tv.isAvailable) return null
        return try {
            tv.getBitmap(sampleBitmap)
            AmbientSampler.fromBitmap(sampleBitmap, samplePixels)
        } catch (e: Throwable) {
            null
        }
    }

    fun release() {
        released = true
        stopPlayer()
        container = null
        texture = null
    }
}

/**
 * Video latar yang diputar berulang tanpa henti, tanpa suara kecuali [volume] > 0. Jeda otomatis
 * saat aplikasi tidak di layar depan. Bila [ambient] tidak null, warna tepi bingkai dikirim ke
 * sana beberapa kali per detik untuk efek cahaya.
 */
@Composable
fun HomeVideoLayer(
    file: File,
    volume: Float,
    playing: Boolean,
    ambient: AmbientState?,
    onFrameReady: (Boolean) -> Unit,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resumed = rememberIsResumed()
    val shouldPlay = playing && resumed

    val controller = remember(file) { HomeVideoController(file) }
    val frameCallback by rememberUpdatedState(onFrameReady)
    val failCallback by rememberUpdatedState(onFailed)
    remember(controller) {
        controller.onFrame = { frameCallback(it) }
        controller.onError = { failCallback() }
        true
    }

    LaunchedEffect(controller, shouldPlay, volume) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        controller.setState(shouldPlay, volume) { audio?.isMusicActive == true }
    }

    if (ambient != null && shouldPlay) {
        LaunchedEffect(controller, ambient) {
            delay(400)
            while (true) {
                controller.sample()?.let { ambient.colors = it }
                delay(AmbientSampler.INTERVAL_MS)
            }
        }
    }

    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    AndroidView(
        factory = { ctx -> controller.createView(ctx) },
        modifier = modifier
    )
}

/** True selama layar aplikasi ini berada di depan (RESUMED); dipakai untuk menjeda animasi. */
@Composable
fun rememberIsResumed(): Boolean {
    val context = LocalContext.current
    val owner = remember(context) { findLifecycleOwner(context) }
    var resumed by remember(owner) {
        mutableStateOf(owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: true)
    }
    DisposableEffect(owner) {
        val lifecycle = owner?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed = true
                Lifecycle.Event.ON_PAUSE -> resumed = false
                else -> {}
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    return resumed
}

private fun findLifecycleOwner(context: Context): LifecycleOwner? {
    var c: Context? = context
    while (c is ContextWrapper) {
        if (c is LifecycleOwner) return c
        c = c.baseContext
    }
    return null
}

/** True bila mode Hemat Baterai HP sedang menyala; ikut berubah saat pengguna menyalakannya. */
@Composable
fun rememberPowerSaveMode(): Boolean {
    val appContext = LocalContext.current.applicationContext
    val powerManager = remember { appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    var saving by remember { mutableStateOf(powerManager?.isPowerSaveMode ?: false) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                saving = powerManager?.isPowerSaveMode ?: false
            }
        }
        try {
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (_: Throwable) {
        }
        onDispose {
            try {
                appContext.unregisterReceiver(receiver)
            } catch (_: Throwable) {
            }
        }
    }
    return saving
}
