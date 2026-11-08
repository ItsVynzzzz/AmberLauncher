package com.israadev.nuxlauncher.core.game.input

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.ui.i18n.Tr

/**
 * Salinan ringan dari huruf yang dikirim ke game lewat keyboard layar. Dipakai untuk preview ketikan: keyboard
 * menutupi kotak chat game, jadi pemain tidak bisa memeriksa tulisannya sebelum menekan kirim.
 *
 * Yang disalin hanya huruf yang dikirim sejak keyboard dibuka (huruf, hapus, kirim), jadi teks yang sudah ada di
 * kotak game sebelumnya (mis. "/" yang diisi sendiri oleh game) tidak ikut tampil. Tidak ada animasi, tidak ada
 * hitung ulang tata letak: satu string kecil yang berganti tiap ketukan.
 */
@Stable
class TypingMirror {
    private val buf = StringBuilder()

    /** Bagian ujung ketikan yang ditampilkan (paling akhir diketik). */
    var shown by mutableStateOf("")
        private set

    fun append(c: Char) {
        // Karakter kontrol tidak ditampilkan di bar satu baris.
        if (c < ' ') return
        buf.append(c)
        if (buf.length > MAX_KEEP) buf.delete(0, buf.length - MAX_KEEP)
        publish()
    }

    fun backspace() {
        if (buf.isEmpty()) return
        buf.setLength(buf.length - 1)
        publish()
    }

    fun clear() {
        if (buf.isEmpty()) return
        buf.setLength(0)
        publish()
    }

    private fun publish() {
        val len = buf.length
        shown = if (len <= SHOW_CHARS) {
            buf.toString()
        } else {
            var from = len - SHOW_CHARS
            // Jangan memotong tepat di tengah pasangan surrogate (emoji).
            if (Character.isLowSurrogate(buf[from])) from++
            "…" + buf.substring(from)
        }
    }

    private companion object {
        /** Panjang maksimum ketikan yang disimpan (chat Minecraft paling panjang 256). */
        const val MAX_KEEP = 1024

        /** Jumlah karakter terakhir yang ditampilkan; dengan dua baris cukup di layar sempit sekalipun. */
        const val SHOW_CHARS = 80
    }
}

private const val HINT = "Ketikanmu tampil di sini"

/**
 * Bar preview tepat di atas keyboard. Tanpa klik dan tanpa animasi, jadi sentuhan tetap lewat ke lapisan di
 * bawahnya. Digambar hanya selama keyboard layar benar-benar terlihat (keyboard fisik tidak menutupi layar);
 * pengecekannya ada di sini supaya layar game tidak ikut tersusun ulang saat keyboard muncul atau hilang.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TypingPreviewBar(mirror: TypingMirror) {
    if (!WindowInsets.isImeVisible) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 4.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        val typed = mirror.shown
        // material3.Text langsung (bukan pembungkus terjemahan): yang diketik pemain tidak boleh diterjemahkan.
        androidx.compose.material3.Text(
            text = if (typed.isEmpty()) Tr.t(HINT) else typed,
            color = if (typed.isEmpty()) Color(0xFF9AA0A6) else Color.White,
            fontSize = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .background(Color(0xE6101418), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
