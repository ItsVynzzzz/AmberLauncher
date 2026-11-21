package com.israadev.nuxlauncher.ui.streamer

import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text as UserText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.streamer.QrCode
import com.israadev.nuxlauncher.core.streamer.Stopwatch
import com.israadev.nuxlauncher.core.streamer.StreamerImages
import com.israadev.nuxlauncher.core.streamer.StreamerItem
import com.israadev.nuxlauncher.core.streamer.StreamerLogic
import com.israadev.nuxlauncher.ui.components.AmberIcon
import com.israadev.nuxlauncher.ui.components.amberTap
import com.israadev.nuxlauncher.ui.dialogs.MenuDivider
import com.israadev.nuxlauncher.ui.dialogs.MenuGroup
import com.israadev.nuxlauncher.ui.dialogs.MenuSwitch
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.theme.AmberUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/*
 * Overlay streamer di dalam game: teks, gambar, link (dengan kode QR), dan timer yang melayang di
 * enam posisi layar, plus sidebar kiri yang tersembunyi untuk menyalakan/mematikannya saat bermain.
 *
 * Ringan: item hanya komponen biasa tanpa animasi dan tanpa blur. Satu-satunya yang berubah terus
 * adalah timer, dan itu hanya menggambar ulang satu teks kecil sekali per detik, hanya saat berjalan.
 * Item tidak menangkap sentuhan, jadi kontrol dan kamera di bawahnya tetap bekerja. Hanya gagang
 * kecil di tepi kiri dan panelnya yang menerima sentuhan.
 */

/** Stopwatch satu item timer. Angka detik dibaca lewat state supaya hanya teks timer yang tergambar ulang. */
internal class StreamerTimer {
    val watch = Stopwatch { SystemClock.elapsedRealtime() }

    var running by mutableStateOf(false)
        private set

    var seconds by mutableLongStateOf(0L)
        private set

    fun refresh() {
        seconds = watch.elapsedMs() / 1000L
    }

    fun toggle() {
        if (watch.running) watch.pause() else watch.start()
        running = watch.running
        refresh()
    }

    fun reset() {
        watch.reset()
        refresh()
    }
}

/**
 * Keadaan overlay selama game berjalan (di memori saja): item mana yang sedang tampil dan timer-nya.
 * Tidak disimpan ke pengaturan, jadi menyalakan atau mematikan di dalam game tidak mengubah apa pun
 * di berkas pengaturan; permainan berikutnya mulai lagi dari pilihan "Tampil saat game mulai".
 */
object StreamerRuntime {
    private val shown = mutableStateMapOf<String, Boolean>()
    private val timers = HashMap<String, StreamerTimer>()

    fun isShown(item: StreamerItem): Boolean = shown[item.id] ?: item.on

    fun toggle(item: StreamerItem) {
        shown[item.id] = !isShown(item)
    }

    internal fun timer(id: String): StreamerTimer = timers.getOrPut(id) { StreamerTimer() }
}

private val ChipShape = RoundedCornerShape(10.dp)
private val ChipEdge = Color(0x33FFFFFF)

/** Nama posisi untuk tampilan (kunci terjemahan). */
internal fun posLabel(pos: String): String = when (pos) {
    StreamerLogic.POS_TOP_START -> "Kiri atas"
    StreamerLogic.POS_TOP_END -> "Kanan atas"
    StreamerLogic.POS_BOTTOM_START -> "Kiri bawah"
    StreamerLogic.POS_BOTTOM -> "Bawah"
    StreamerLogic.POS_BOTTOM_END -> "Kanan bawah"
    else -> "Atas"
}

internal fun typeLabel(type: String): String = when (type) {
    StreamerLogic.TYPE_IMAGE -> "Gambar"
    StreamerLogic.TYPE_LINK -> "Link"
    StreamerLogic.TYPE_TIMER -> "Timer"
    else -> "Teks"
}

private fun alignmentOf(pos: String): Alignment = when (pos) {
    StreamerLogic.POS_TOP_START -> Alignment.TopStart
    StreamerLogic.POS_TOP_END -> Alignment.TopEnd
    StreamerLogic.POS_BOTTOM_START -> Alignment.BottomStart
    StreamerLogic.POS_BOTTOM -> Alignment.BottomCenter
    StreamerLogic.POS_BOTTOM_END -> Alignment.BottomEnd
    else -> Alignment.TopCenter
}

private fun horizontalOf(pos: String): Alignment.Horizontal = when (pos) {
    StreamerLogic.POS_TOP_START, StreamerLogic.POS_BOTTOM_START -> Alignment.Start
    StreamerLogic.POS_TOP_END, StreamerLogic.POS_BOTTOM_END -> Alignment.End
    else -> Alignment.CenterHorizontally
}

/** Kode QR sebagai gambar: modul hitam di atas putih, dengan tepi putih dua modul supaya mudah dipindai. */
internal fun qrBitmap(payload: String): ImageBitmap? {
    val m = QrCode.encode(payload) ?: return null
    val quiet = 2
    val n = m.size + 2 * quiet
    val px = IntArray(n * n) { AColor.WHITE }
    for (y in m.indices) {
        for (x in m.indices) {
            if (m[y][x]) px[(y + quiet) * n + (x + quiet)] = AColor.BLACK
        }
    }
    return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** Memuat gambar overlay di thread latar; null selama belum siap atau bila berkas hilang. */
@Composable
internal fun rememberStreamerBitmap(name: String): ImageBitmap? {
    val context = LocalContext.current
    val state = produceState<ImageBitmap?>(null, name) {
        value = withContext(Dispatchers.IO) { StreamerImages.load(context, name)?.asImageBitmap() }
    }
    return state.value
}

/**
 * Lapisan overlay streamer. [items] boleh berisi item yang belum layak tampil; yang kosong disaring di sini.
 * [ghostHandle] menyembunyikan gagang (tetap bisa disentuh), dipakai saat merekam "Game saja".
 * [chromeVisible] = false menyembunyikan gagang dan panel cepat (mis. saat menu pengaturan game terbuka,
 * karena menu itu sudah membawa panel overlay sendiri); item overlay tetap tampil.
 */
@Composable
fun StreamerLayer(
    items: List<StreamerItem>,
    panelOpen: Boolean,
    onPanelChange: (Boolean) -> Unit,
    ghostHandle: Boolean,
    chromeVisible: Boolean = true
) {
    val list = remember(items) {
        StreamerLogic.sanitize(items).filter { StreamerLogic.isDisplayable(it) }
    }
    val maxWidthDp = LocalConfiguration.current.screenWidthDp * 0.42f

    Box(modifier = Modifier.fillMaxSize()) {
        // Item overlay: tanpa penangan sentuhan, jadi sentuhan lewat ke kontrol dan game di bawahnya.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(8.dp)
        ) {
            for (pos in StreamerLogic.POSITIONS) {
                val group = list.filter { it.pos == pos && StreamerRuntime.isShown(it) }
                if (group.isEmpty()) continue
                Column(
                    modifier = Modifier.align(alignmentOf(pos)),
                    horizontalAlignment = horizontalOf(pos),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (item in group) {
                        key(item.id) { StreamerItemView(item, maxWidthDp) }
                    }
                }
            }
        }

        // Tanpa gagang dan panel saat menu pengaturan game terbuka: menu itu mengurus overlay sendiri.
        if (chromeVisible) {
            if (panelOpen) {
                StreamerPanel(
                    items = list,
                    onClose = { onPanelChange(false) },
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            } else {
                StreamerHandle(
                    ghost = ghostHandle,
                    onOpen = { onPanelChange(true) },
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }
        }
    }
}

/**
 * Gaya tulisan item. Tanpa latar, tulisan diberi bayangan keras satu lapis (tanpa blur) supaya tetap
 * terbaca di atas adegan game yang terang; dengan latar, kotak kacanya yang menjaga keterbacaan.
 */
@Composable
private fun itemTextStyle(item: StreamerItem): TextStyle {
    val base = LocalTextStyle.current
    return if (item.bg) {
        base
    } else {
        base.copy(shadow = Shadow(Color(StreamerLogic.shadowArgb(item.color)), Offset(1.5f, 1.5f), 0f))
    }
}

@Composable
private fun StreamerItemView(item: StreamerItem, maxWidthDp: Float) {
    when (item.type) {
        StreamerLogic.TYPE_TEXT -> Chip(item, Modifier.widthIn(max = maxWidthDp.dp)) {
            UserText(
                text = item.text,
                color = Color(StreamerLogic.textArgb(item.color)),
                fontSize = StreamerLogic.textSp(item.size).sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = itemTextStyle(item)
            )
        }
        StreamerLogic.TYPE_LINK -> LinkChip(item, maxWidthDp)
        StreamerLogic.TYPE_IMAGE -> ImageItem(item, maxWidthDp)
        StreamerLogic.TYPE_TIMER -> Chip(item) {
            TimerText(
                timer = StreamerRuntime.timer(item.id),
                fontSizeSp = StreamerLogic.textSp(item.size),
                color = Color(StreamerLogic.textArgb(item.color)),
                style = itemTextStyle(item)
            )
        }
    }
}

/**
 * Tempat teks, link, dan timer digambar. Dengan latar: kotak kaca dengan tepi putih tipis (gelap, atau
 * terang bila tulisannya hitam). Tanpa latar: hanya tulisannya, tanpa lapisan gambar tambahan.
 */
@Composable
private fun Chip(item: StreamerItem, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val framed = if (item.bg) {
        modifier
            .clip(ChipShape)
            .background(Color(StreamerLogic.chipBgArgb(item.color)))
            .border(1.dp, ChipEdge, ChipShape)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    } else {
        modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    }
    Column(
        modifier = framed,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

@Composable
private fun LinkChip(item: StreamerItem, maxWidthDp: Float) {
    val qr = remember(item.text, item.qr) {
        if (item.qr) qrBitmap(StreamerLogic.qrPayload(item.text)) else null
    }
    Chip(item, Modifier.widthIn(max = maxWidthDp.dp)) {
        if (qr != null) {
            Image(
                bitmap = qr,
                contentDescription = null,
                filterQuality = FilterQuality.None,
                modifier = Modifier
                    .size(StreamerLogic.qrSizeDp(item.size).dp)
                    .clip(RoundedCornerShape(6.dp))
            )
            Spacer(modifier = Modifier.height(5.dp))
        }
        UserText(
            text = StreamerLogic.linkDisplay(item.text),
            color = Color(StreamerLogic.textArgb(item.color)),
            fontSize = StreamerLogic.linkSp(item.size).sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = itemTextStyle(item)
        )
    }
}

@Composable
private fun ImageItem(item: StreamerItem, maxWidthDp: Float) {
    val bmp = rememberStreamerBitmap(item.image) ?: return
    val (w, h) = StreamerLogic.fitSize(
        ratio = bmp.width.toFloat() / bmp.height.toFloat(),
        maxH = StreamerLogic.imageHeightDp(item.size).toFloat(),
        maxW = maxWidthDp
    )
    Image(
        bitmap = bmp,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .size(w.dp, h.dp)
            .clip(RoundedCornerShape(8.dp))
    )
}

/**
 * Angka timer. Berdetak hanya selama timer berjalan, tepat di pergantian detik; saat tampil
 * kembali setelah disembunyikan, angkanya langsung disegarkan.
 */
@Composable
private fun TimerText(
    timer: StreamerTimer,
    fontSizeSp: Int,
    color: Color,
    style: TextStyle = LocalTextStyle.current
) {
    val running = timer.running
    LaunchedEffect(timer, running) {
        timer.refresh()
        while (running) {
            delay(timer.watch.msToNextSecond() + 8L)
            timer.refresh()
        }
    }
    UserText(
        text = StreamerLogic.formatElapsed(timer.seconds * 1000L),
        color = color,
        fontSize = fontSizeSp.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        style = style
    )
}

/** Gagang kecil di tepi kiri layar; ketuk untuk membuka panel. */
@Composable
private fun StreamerHandle(ghost: Boolean, onOpen: () -> Unit, modifier: Modifier) {
    val currentOnOpen by rememberUpdatedState(onOpen)
    val shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
    Box(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Start))
            .size(width = 34.dp, height = 96.dp)
            .pointerInput(Unit) { detectTapGestures { currentOnOpen() } },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(width = 20.dp, height = 60.dp)
                .then(if (ghost) Modifier.graphicsLayer { alpha = 0f } else Modifier)
                .clip(shape)
                .background(Color(0x99121217))
                .border(1.dp, ChipEdge, shape),
            contentAlignment = Alignment.Center
        ) {
            AmberIcon("chev_right", 12.dp, AmberUi.TextDim)
        }
    }
}

/**
 * Panel di sisi kiri: daftar item dengan saklar tampil/sembunyi, dan kontrol timer. Bukan dialog:
 * game tetap berjalan di belakangnya. Latarnya menelan sentuhan supaya jari yang menyentuh panel
 * tidak ikut menggerakkan kamera atau menekan kontrol di bawahnya.
 */
@Composable
private fun StreamerPanel(items: List<StreamerItem>, onClose: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(290.dp)
            .clip(shape)
            .background(AmberUi.MenuCard)
            .border(1.dp, AmberUi.Border, shape)
            .pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(
                WindowInsets.displayCutout.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Mode streamer",
                    color = AmberUi.Text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Ketuk untuk menampilkan atau menyembunyikan.",
                    color = AmberUi.TextDim,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(AmberUi.SurfaceRaised)
                    .amberTap(onClose),
                contentAlignment = Alignment.Center
            ) {
                AmberIcon("close", 13.dp, AmberUi.Text)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            if (items.isEmpty()) {
                Text(
                    text = "Belum ada overlay. Tambahkan di Pengaturan > Game > Mode streamer > Atur overlay.",
                    color = AmberUi.TextDim,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                MenuGroup {
                    items.forEachIndexed { index, item ->
                        if (index > 0) MenuDivider()
                        key(item.id) { PanelRow(item) }
                    }
                }
            }
        }
    }
}

/**
 * Satu baris item di panel: ketuk untuk menampilkan atau menyembunyikan. Bila [onEdit] diberikan
 * (panel di menu pengaturan game), ada tombol kecil untuk mengubah item itu.
 */
@Composable
internal fun PanelRow(item: StreamerItem, onEdit: (() -> Unit)? = null) {
    val on = StreamerRuntime.isShown(item)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .amberTap { StreamerRuntime.toggle(item) }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.type == StreamerLogic.TYPE_IMAGE) {
                PanelThumb(item.image)
                Spacer(modifier = Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                ItemTitle(item, 13)
                Text(
                    text = posLabel(item.pos),
                    color = AmberUi.TextDim,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            if (onEdit != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(AmberUi.SurfaceRaised)
                        .amberTap(onEdit),
                    contentAlignment = Alignment.Center
                ) {
                    AmberIcon("sliders", 14.dp, AmberUi.Text)
                }
                Spacer(modifier = Modifier.width(10.dp))
            }
            MenuSwitch(on)
        }
        if (item.type == StreamerLogic.TYPE_TIMER) TimerControls(StreamerRuntime.timer(item.id))
    }
}

/** Judul item: teks dan link tampil apa adanya (tidak diterjemahkan), jenis lain memakai nama jenisnya. */
@Composable
internal fun ItemTitle(item: StreamerItem, fontSizeSp: Int) {
    val user = when (item.type) {
        StreamerLogic.TYPE_TEXT -> item.text
        StreamerLogic.TYPE_LINK -> StreamerLogic.linkDisplay(item.text)
        else -> ""
    }
    if (user.isNotEmpty()) {
        UserText(
            text = user,
            color = AmberUi.Text,
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    } else {
        Text(
            text = typeLabel(item.type),
            color = AmberUi.Text,
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun PanelThumb(name: String) {
    val bmp = rememberStreamerBitmap(name)
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(shape)
            .background(AmberUi.SurfaceRaised)
            .border(1.dp, AmberUi.Border, shape),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun TimerControls(timer: StreamerTimer) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            TimerText(timer = timer, fontSizeSp = 18, color = AmberUi.Text)
        }
        PillButton(
            label = if (timer.running) "Jeda" else "Mulai",
            primary = !timer.running,
            onClick = { timer.toggle() }
        )
        PillButton(label = "Reset", primary = false, onClick = { timer.reset() })
    }
}

@Composable
private fun PillButton(label: String, primary: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(shape)
            .then(
                if (primary) Modifier.background(AmberUi.AccentBrush, shape)
                else Modifier.background(AmberUi.SurfaceRaised, shape)
            )
            .border(1.dp, if (primary) AmberUi.AccentEdge else AmberUi.BorderStrong, shape)
            .amberTap(onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = AmberUi.Text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
