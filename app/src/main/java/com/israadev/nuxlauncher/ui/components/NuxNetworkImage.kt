package com.israadev.nuxlauncher.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.ui.theme.NuxColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val IMAGE_HTTP_CACHE_BYTES = 40L * 1024L * 1024L
private const val IMAGE_MAX_DOWNLOAD_BYTES = 12L * 1024L * 1024L
private const val IMAGE_DEFAULT_MAX_SIDE_PX = 512

/**
 * Cache memori berbasis UKURAN BYTE (bukan jumlah gambar): 50 gambar besar tidak lagi bisa
 * menghabiskan puluhan MB RAM. Batas = 1/12 heap aplikasi, antara 8 dan 32 MB.
 */
private val imageCache: LruCache<String, ImageBitmap> by lazy {
    val maxBytes = (Runtime.getRuntime().maxMemory() / 12L)
        .coerceIn(8L * 1024L * 1024L, 32L * 1024L * 1024L)
        .toInt()
    object : LruCache<String, ImageBitmap>(maxBytes) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            val bytes = value.width.toLong() * value.height.toLong() * 4L
            return if (bytes > Int.MAX_VALUE.toLong()) Int.MAX_VALUE else bytes.toInt()
        }
    }
}

private val imageClientLock = Any()

@Volatile
private var imageClientInstance: OkHttpClient? = null

/**
 * Klien HTTP gambar dengan cache DISK (40 MB di folder cache aplikasi): ikon mod / avatar yang
 * sudah pernah dibuka tidak diunduh ulang setiap aplikasi dibuka atau daftar di-scroll.
 */
private fun imageHttpClient(context: Context): OkHttpClient {
    imageClientInstance?.let { return it }
    return synchronized(imageClientLock) {
        imageClientInstance ?: buildImageHttpClient(context.applicationContext).also {
            imageClientInstance = it
        }
    }
}

private fun buildImageHttpClient(appContext: Context): OkHttpClient {
    val builder = OkHttpClient.Builder()
        .dns(com.israadev.nuxlauncher.core.network.NuxDns)
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
    try {
        builder.cache(Cache(File(appContext.cacheDir, "nux_image_http"), IMAGE_HTTP_CACHE_BYTES))
        // Gambar jarang berubah: simpan 7 hari walau server tidak mengirim header cache yang benar.
        builder.addNetworkInterceptor(
            Interceptor { chain ->
                val response = chain.proceed(chain.request())
                if (response.isSuccessful) {
                    response.newBuilder()
                        .removeHeader("Pragma")
                        .header("Cache-Control", "public, max-age=604800")
                        .build()
                } else {
                    response
                }
            }
        )
    } catch (_: Throwable) {
        // Tanpa cache disk bila foldernya tidak bisa dibuat; gambar tetap tampil.
    }
    return builder.build()
}

/**
 * Menjalankan panggilan HTTP dan membaca isi gambarnya di thread OkHttp. Dapat dibatalkan:
 * saat item keluar dari layar (coroutine dibatalkan) unduhan ikut dihentikan, jadi scroll
 * cepat di daftar mod tidak menumpuk puluhan unduhan yang tak terpakai.
 */
private suspend fun Call.awaitImageBytes(maxBytes: Long): ByteArray? =
    suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation {
            try {
                this@awaitImageBytes.cancel()
            } catch (_: Throwable) {
            }
        }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val bytes: ByteArray? = response.use { r ->
                        if (!r.isSuccessful) {
                            null
                        } else {
                            val body = r.body
                            if ((body?.contentLength() ?: -1L) > maxBytes) null else body?.bytes()
                        }
                    }
                    if (cont.isActive) cont.resume(bytes)
                } catch (e: Throwable) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            }
        })
    }

/**
 * Pangkat dua terbesar yang masih menjaga sisi terpanjang gambar >= [targetPx].
 * Gambar 2000x2000 yang cuma tampil 40dp tidak perlu dimuat penuh (16 MB) di RAM.
 */
private fun sampleSizeFor(width: Int, height: Int, targetPx: Int): Int {
    if (width <= 0 || height <= 0 || targetPx <= 0) return 1
    val longest = maxOf(width, height)
    var sample = 1
    while (longest / (sample * 2) >= targetPx) sample *= 2
    return sample
}

private fun decodeSampled(bytes: ByteArray, targetPx: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, targetPx)
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
}

private fun decodeSampledFromUri(context: Context, uri: Uri, targetPx: Int): ImageBitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, bounds)
    }
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, targetPx)
    }
    return resolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, opts)
    }?.asImageBitmap()
}

private suspend fun loadImageBitmap(context: Context, model: Any?, targetPx: Int): ImageBitmap? {
    return try {
        when (model) {
            is Uri -> withContext(Dispatchers.IO) { decodeSampledFromUri(context, model, targetPx) }
            is String -> when {
                model.startsWith("http://") || model.startsWith("https://") -> {
                    // Gambar selalu lewat HTTPS (alamat http:// dinaikkan), supaya tidak bisa disadap atau diganti
                    val secureUrl = if (model.startsWith("http://")) "https://" + model.removePrefix("http://") else model
                    val request = Request.Builder().url(secureUrl).build()
                    val bytes = imageHttpClient(context).newCall(request).awaitImageBytes(IMAGE_MAX_DOWNLOAD_BYTES)
                    if (bytes == null || bytes.isEmpty()) {
                        null
                    } else {
                        withContext(Dispatchers.Default) { decodeSampled(bytes, targetPx) }
                    }
                }
                model.startsWith("content://") || model.startsWith("file://") ->
                    withContext(Dispatchers.IO) { decodeSampledFromUri(context, Uri.parse(model), targetPx) }
                else -> null
            }
            else -> null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        null
    }
}

private fun imageCacheKey(model: Any?, targetPx: Int): String? {
    if (model == null) return null
    val raw = model.toString()
    if (raw.isBlank()) return null
    return "$raw|$targetPx"
}

/**
 * Neo-Brutalist Network & Local Uri Image Loader dengan cache memori (byte), cache disk,
 * decode hemat memori (downsample sesuai [maxSidePx]) dan pembatalan saat keluar layar.
 *
 * @param maxSidePx perkiraan sisi terpanjang gambar yang benar-benar dibutuhkan di layar
 *        (dalam piksel). Makin kecil makin hemat RAM; ikon 38-48dp cukup 192.
 * @param showSpinner tampilkan indikator putar saat memuat (hanya untuk gambar besar; ikon
 *        kecil memakai latar statis supaya daftar panjang tidak menggambar puluhan animasi).
 */
@Composable
fun NuxNetworkImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    fallbackInitials: String = "N",
    shape: Shape = RoundedCornerShape(12.dp),
    contentScale: ContentScale = ContentScale.Crop,
    maxSidePx: Int = IMAGE_DEFAULT_MAX_SIDE_PX,
    showSpinner: Boolean = false
) {
    val context = LocalContext.current
    val cacheKey = remember(model, maxSidePx) { imageCacheKey(model, maxSidePx) }
    var imageBitmap by remember(cacheKey) {
        mutableStateOf<ImageBitmap?>(cacheKey?.let { imageCache.get(it) })
    }
    var isLoading by remember(cacheKey) {
        mutableStateOf(cacheKey != null && imageBitmap == null)
    }

    LaunchedEffect(cacheKey) {
        val key = cacheKey
        if (key == null) {
            imageBitmap = null
            isLoading = false
            return@LaunchedEffect
        }
        if (imageBitmap != null) {
            isLoading = false
            return@LaunchedEffect
        }

        isLoading = true
        try {
            val loaded = loadImageBitmap(context, model, maxSidePx)
            if (loaded != null) {
                imageCache.put(key, loaded)
                imageBitmap = loaded
            }
        } finally {
            isLoading = false
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(NuxColors.SurfaceWhite),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = imageBitmap
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        } else if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NuxColors.LightGray.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                if (showSpinner) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = NuxColors.SageGreen,
                        strokeWidth = 2.dp
                    )
                }
            }
        } else {
            // Fallback Initials Display with Neo-Brutalist gaming colors
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NuxColors.SoftLime),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = fallbackInitials.take(2).uppercase(),
                    color = NuxColors.SageGreen,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
