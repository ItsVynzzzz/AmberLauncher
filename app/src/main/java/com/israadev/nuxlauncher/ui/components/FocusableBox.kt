package com.israadev.nuxlauncher.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon

/**
 * Kotak yang bisa menerima fokus.
 *
 * [hoverIcon]: bentuk penunjuk sistem Android saat mouse fisik melayang di atas kotak ini. Berupa State
 * yang dibaca DI SINI (bukan oleh pemanggil), jadi bentuk yang berganti hanya menyusun ulang kotak
 * kosong ini, bukan layar di sekitarnya. Null = pakai penunjuk bawaan sistem.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FocusableBox(
    modifier: Modifier = Modifier,
    requestKey: Any? = null,
    canRequestFocus: () -> Boolean = { true },
    hoverIcon: State<PointerIcon>? = null
) {
    val focusRequester = remember { FocusRequester() }
    val currentCanRequestFocus by rememberUpdatedState(canRequestFocus)

    // Penanda ikon diletakkan paling depan di rantai modifier (sama seperti Zalith), sebelum penangan
    // sentuh/mouse milik pemanggil.
    val iconModifier = if (hoverIcon != null) Modifier.pointerHoverIcon(hoverIcon.value) else Modifier

    Box(
        modifier = iconModifier
            .then(modifier)
            .focusRequester(focusRequester)
            .focusable(enabled = true)
    )

    LaunchedEffect(requestKey) {
        if (currentCanRequestFocus()) {
            focusRequester.requestFocus()
        }
    }
}
