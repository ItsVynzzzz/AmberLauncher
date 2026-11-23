package com.israadev.nuxlauncher.ui.streamer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.israadev.nuxlauncher.core.streamer.StreamerLogic

/**
 * Satu overlay web yang harus tampil: id item, alamat https yang sudah divalidasi, kepekatannya, dan kotaknya di layar
 * (pecahan layar; lihat [StreamerLogic.webRect]) beserta skala halamannya (1 = 100%).
 */
internal data class StreamerWebSpec(
    val id: String,
    val url: String,
    val alpha: Float,
    val x: Float = -1f,
    val y: Float = -1f,
    val boxW: Float = 0f,
    val boxH: Float = 0f,
    val zoom: Float = 1f
)

/**
 * Lapisan overlay web: menjalankan link overlay donasi (Saweria, Sociabuzz, Trakteer, StreamElements, Streamlabs,
 * dll) sebagai browser source di atas game. Semua jenisnya (Alert, Media Share, Song Share, Subathon, Wheel, Goal,
 * Top 10, Poll, dan seterusnya) hanyalah halaman web, jadi satu mekanisme melayani semuanya.
 *
 * - Tiap overlay = satu WebView dengan latar transparan (layar penuh atau kotak yang diatur pengguna), di dalam wadah
 *   yang menaruh sentuhan dan gerakan mouse ke game di bawahnya (WebView tidak pernah menerima sentuhan).
 * - Skala halaman di bawah 100%: WebView dibuat lebih besar dari kotaknya (halaman ditata untuk layar yang lebih
 *   lebar) lalu dikecilkan ke kotak, seperti ukuran kanvas browser source di OBS.
 * - WebView hanya dibuat saat overlay tampil dan dibuang saat disembunyikan atau item dihapus, supaya tidak
 *   memakan RAM selama tidak dipakai. Wadah ditambahkan ke jendela di atas Compose, karena sentuhan melewati
 *   View biasa dengan benar, sedangkan AndroidView di dalam Compose ikut menelan sentuhan.
 * - Alamat tidak pernah dicatat ke log.
 */
@Composable
internal fun StreamerWebLayer(specs: List<StreamerWebSpec>, visible: Boolean) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() } ?: return
    val host = remember(activity) { StreamerWebHost(activity) }

    DisposableEffect(host) {
        host.attach()
        val owner = activity as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> host.pause()
                Lifecycle.Event.ON_RESUME -> host.resume()
                else -> Unit
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose {
            owner?.lifecycle?.removeObserver(observer)
            host.destroy()
        }
    }

    LaunchedEffect(host, specs, visible) { host.update(specs, visible) }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Wadah yang tidak pernah menerima sentuhan atau gerakan mouse: semuanya jatuh ke game di bawahnya. */
private class PassThroughFrame(context: Context) : FrameLayout(context) {
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean = false
    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean = false
}

private class WebEntry(var spec: StreamerWebSpec, val view: WebView) {
    var retries = 0
    var retry: Runnable? = null
}

/** Pengelola WebView untuk satu aktivitas game. Dipakai hanya dari thread utama. */
private class StreamerWebHost(private val activity: Activity) {
    private val container = PassThroughFrame(activity).apply {
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }
    private val entries = LinkedHashMap<String, WebEntry>()
    private var wanted: List<StreamerWebSpec> = emptyList()
    private var visible = true
    private var paused = false
    private var attached = false
    private var destroyed = false

    fun attach() {
        if (attached || destroyed) return
        attached = true
        activity.addContentView(
            container,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        // Ukuran layar berubah (mis. diputar): kotak tiap overlay dihitung ulang. Ditunda ke luar proses layout.
        container.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val sizeChanged = right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop
            if (sizeChanged) container.post { if (!destroyed) layoutAll() }
        }
    }

    /** Taruh dan ukur satu WebView sesuai [WebEntry.spec] dan ukuran wadah saat ini (belum ada ukuran: ditunda). */
    private fun layoutEntry(entry: WebEntry) {
        val cw = container.width
        val ch = container.height
        if (cw <= 0 || ch <= 0) return
        val spec = entry.spec
        val rect = StreamerLogic.webRect(cw, ch, spec.x, spec.y, spec.boxW, spec.boxH)
        val zoom = spec.zoom.coerceIn(StreamerLogic.ZOOM_MIN, 1f)
        // Halaman ditata seluas kotak / skala, lalu dikecilkan dari pojok kiri atas supaya pas di kotaknya.
        val lw = Math.round(rect.width / zoom)
        val lh = Math.round(rect.height / zoom)
        val view = entry.view
        val lp = (view.layoutParams as? FrameLayout.LayoutParams) ?: FrameLayout.LayoutParams(lw, lh)
        if (lp.width != lw || lp.height != lh || lp.leftMargin != rect.left || lp.topMargin != rect.top) {
            lp.width = lw
            lp.height = lh
            lp.leftMargin = rect.left
            lp.topMargin = rect.top
            lp.gravity = Gravity.TOP or Gravity.START
            view.layoutParams = lp
        }
        view.pivotX = 0f
        view.pivotY = 0f
        view.scaleX = zoom
        view.scaleY = zoom
    }

    private fun layoutAll() {
        for (entry in entries.values) layoutEntry(entry)
    }

    fun update(specs: List<StreamerWebSpec>, visible: Boolean) {
        if (destroyed) return
        wanted = specs
        this.visible = visible
        container.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        reconcile()
    }

    /** Samakan WebView yang hidup dengan [wanted]: buang yang tidak diminta lagi atau berubah alamat, buat yang baru. */
    private fun reconcile() {
        val keep = wanted.associateBy { it.id }
        for ((id, entry) in entries.entries.toList()) {
            val want = keep[id]
            if (want == null || want.url != entry.spec.url) removeEntry(id)
        }
        for (spec in wanted) {
            val existing = entries[spec.id]
            if (existing != null) {
                existing.view.alpha = spec.alpha
                if (existing.spec != spec) {
                    existing.spec = spec
                    layoutEntry(existing)
                }
                continue
            }
            val view = try {
                createWebView(spec)
            } catch (e: Exception) {
                // WebView tidak tersedia atau gagal dibuat: overlay lain dan game tetap jalan.
                null
            } ?: continue
            val entry = WebEntry(spec, view)
            entries[spec.id] = entry
            container.addView(
                view,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
            // Ukuran wadah sudah diketahui: pasang kotaknya sekarang; kalau belum, dilakukan saat wadah selesai diukur.
            layoutEntry(entry)
            if (paused) view.onPause()
            view.loadUrl(spec.url)
        }
    }

    fun pause() {
        paused = true
        entries.values.forEach { it.view.onPause() }
        if (entries.isNotEmpty()) entries.values.first().view.pauseTimers()
    }

    fun resume() {
        paused = false
        entries.values.forEach { it.view.onResume() }
        if (entries.isNotEmpty()) entries.values.first().view.resumeTimers()
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        for (id in entries.keys.toList()) removeEntry(id)
        (container.parent as? ViewGroup)?.removeView(container)
    }

    private fun removeEntry(id: String) {
        val entry = entries.remove(id) ?: return
        entry.retry?.let { container.removeCallbacks(it) }
        val view = entry.view
        container.removeView(view)
        // Masing-masing dibungkus sendiri: proses render yang sudah mati bisa melempar di stopLoading, dan destroy
        // tetap harus jalan agar audio berhenti dan memori kembali.
        try {
            view.stopLoading()
        } catch (_: Exception) {
        }
        try {
            view.destroy()
        } catch (_: Exception) {
        }
    }

    /** Halaman gagal dimuat: kosongkan (jangan biarkan halaman galat tampil di layar) lalu coba lagi nanti. */
    private fun showBlankAndRetry(entry: WebEntry, retry: Boolean) {
        val view = entry.view
        view.loadUrl("about:blank")
        entry.retry?.let { container.removeCallbacks(it) }
        if (!retry || entry.retries >= MAX_RETRIES) return
        entry.retries++
        val r = Runnable {
            if (entries[entry.spec.id] === entry) view.loadUrl(entry.spec.url)
        }
        entry.retry = r
        container.postDelayed(r, RETRY_DELAY_MS)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(spec: StreamerWebSpec): WebView {
        ensureDataDirectorySuffix()
        val view = WebView(activity)
        view.setBackgroundColor(Color.TRANSPARENT)
        view.alpha = spec.alpha
        view.isFocusable = false
        view.isFocusableInTouchMode = false
        view.isClickable = false
        view.isLongClickable = false
        view.isHapticFeedbackEnabled = false
        view.overScrollMode = View.OVER_SCROLL_NEVER
        view.isVerticalScrollBarEnabled = false
        view.isHorizontalScrollBarEnabled = false
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO

        val s = view.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        // Alert bersuara, Song Share, dan Media Share memutar audio/video tanpa ketukan penonton.
        s.mediaPlaybackRequiresUserGesture = false
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        s.javaScriptCanOpenWindowsAutomatically = false
        s.setSupportMultipleWindows(false)
        s.setSupportZoom(false)
        s.builtInZoomControls = false
        s.displayZoomControls = false
        s.setGeolocationEnabled(false)
        s.cacheMode = WebSettings.LOAD_DEFAULT
        // Pemutar tertanam (mis. YouTube di Media Share) sering butuh cookie pihak ketiga.
        try {
            CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
        } catch (_: Exception) {
        }

        view.webViewClient = object : WebViewClient() {
            // Overlay tidak boleh berpindah ke halaman lain: hanya navigasi https yang diizinkan.
            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean =
                !request.url.scheme.equals("https", ignoreCase = true)

            override fun onReceivedError(v: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) entries[spec.id]?.let { showBlankAndRetry(it, retry = true) }
            }

            override fun onReceivedHttpError(v: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (!request.isForMainFrame || response.statusCode < 400) return
                // 4xx: alamat salah atau kedaluwarsa, mengulang tidak berguna. 5xx: gangguan sementara.
                entries[spec.id]?.let { showBlankAndRetry(it, retry = response.statusCode >= 500) }
            }

            // Proses render WebView mati (biasanya kehabisan RAM): tanpa ini seluruh game ikut tertutup.
            override fun onRenderProcessGone(v: WebView, detail: RenderProcessGoneDetail): Boolean {
                removeEntry(spec.id)
                container.postDelayed({ if (!destroyed) reconcile() }, RETRY_DELAY_MS)
                return true
            }
        }
        return view
    }

    private companion object {
        const val RETRY_DELAY_MS = 30_000L
        const val MAX_RETRIES = 20
        private var suffixSet = false

        /**
         * Proses game dan proses launcher sama-sama bisa memakai WebView; sejak Android 9 itu hanya boleh bila
         * folder datanya berbeda. Dipanggil sebelum WebView pertama di proses ini.
         */
        fun ensureDataDirectorySuffix() {
            if (suffixSet) return
            suffixSet = true
            if (Build.VERSION.SDK_INT >= 28) {
                try {
                    WebView.setDataDirectorySuffix("game")
                } catch (_: Exception) {
                    // Sudah ada WebView di proses ini; folder datanya terlanjur dipakai.
                }
            }
        }
    }
}
