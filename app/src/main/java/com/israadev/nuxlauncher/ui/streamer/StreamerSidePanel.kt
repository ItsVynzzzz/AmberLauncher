package com.israadev.nuxlauncher.ui.streamer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.israadev.nuxlauncher.core.streamer.StreamerItem
import com.israadev.nuxlauncher.core.streamer.StreamerLogic
import com.israadev.nuxlauncher.ui.components.AmberButton
import com.israadev.nuxlauncher.ui.theme.AmberUi

/**
 * Sidebar kiri di menu pengaturan game: daftar ringkas overlay (ketuk untuk tampil atau sembunyi) dan satu
 * tombol "Atur di layar" yang membuka editor overlay di atas game ([StreamerEditorLayer]). Sengaja sempit
 * supaya layar game tetap terlihat. Isinya dipasang di jendela menu (lihat GameMenuSheet), jadi hanya dibuat
 * selama menu terbuka dan hilang bersamanya. Ringan: tanpa animasi dan tanpa blur.
 */
@Composable
fun StreamerSidePanel(
    items: List<StreamerItem>,
    onArrange: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val list = remember(items) { StreamerLogic.sanitize(items) }
    val shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
    Column(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(PANEL_WIDTH_FRACTION)
            .widthIn(max = PANEL_MAX_WIDTH_DP.dp)
            .clip(shape)
            .background(AmberUi.MenuCard)
            .border(1.dp, AmberUi.Border, shape)
            // Menelan ketukan di area kosong supaya tidak menutup menu.
            .pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(
                WindowInsets.displayCutout.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)
            )
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        PanelHeader(onClose = onClose)
        Spacer(modifier = Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            PanelList(list)
        }
        Spacer(modifier = Modifier.height(10.dp))
        AmberButton(
            text = "Atur di layar",
            onClick = onArrange,
            height = 40.dp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
