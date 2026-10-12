package com.israadev.nuxlauncher.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.models.InstalledModItem
import com.israadev.nuxlauncher.core.mods.ModIconLoader
import com.israadev.nuxlauncher.ui.theme.NuxColors
import com.israadev.nuxlauncher.ui.theme.NuxSizes
import java.io.File

/**
 * Gambar kecil mod / resource pack yang diambil dari dalam filenya. Bila file tidak punya gambar,
 * ditampilkan dua huruf pertama nama di kotak kaca. [dimmed] = item nonaktif (dibuat pudar).
 */
@Composable
fun ModIcon(
    item: InstalledModItem,
    size: Dp,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(size * 0.26f)
    val initial = remember(item.filePath, item.sizeBytes) { ModIconLoader.peek(item.filePath) }
    val bitmap by produceState<Bitmap?>(initialValue = initial, item.filePath, item.sizeBytes) {
        if (value == null && item.filePath.isNotEmpty()) {
            value = ModIconLoader.load(context, File(item.filePath), item.itemType)
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .alpha(if (dimmed) 0.45f else 1f)
            .clip(shape)
            .background(if (bitmap != null) NuxColors.SurfaceElevated else NuxColors.YellowPale, shape)
            .border(NuxSizes.GlassBorder, NuxColors.CardBorder, shape),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                filterQuality = if (bmp.width <= 48) FilterQuality.None else FilterQuality.Medium
            )
        } else {
            Text(
                text = item.name.take(2).uppercase(),
                color = NuxColors.Yellow,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp
            )
        }
    }
}
