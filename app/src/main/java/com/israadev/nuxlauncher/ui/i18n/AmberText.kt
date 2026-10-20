package com.israadev.nuxlauncher.ui.i18n

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/**
 * Penanda bahasa untuk Compose. Nilainya berubah saat bahasa diganti, sehingga seluruh layar
 * yang membaca teks digambar ulang satu kali tanpa memulai ulang aplikasi.
 */
private val LocalAmberLanguage = staticCompositionLocalOf { 0 }

/** Dipasang sekali di akar UI (MainActivity). */
@Composable
fun AmberLanguageHost(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAmberLanguage provides Tr.version.intValue) {
        content()
    }
}

/**
 * Pengganti `androidx.compose.material3.Text` yang menerjemahkan teks ke bahasa pilihan.
 * Parameternya sama persis, jadi cukup mengganti import-nya. Terjemahan terjadi di titik gambar
 * teks, sehingga semua teks yang lewat komponen mana pun ikut diterjemahkan.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current
) {
    val language = LocalAmberLanguage.current
    androidx.compose.material3.Text(
        text = if (language >= 0) Tr.t(text) else text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}
