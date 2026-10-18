package com.israadev.nuxlauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.israadev.nuxlauncher.ui.theme.NuxColors
import com.israadev.nuxlauncher.ui.theme.NuxSizes
import com.israadev.nuxlauncher.ui.theme.LocalNuxScale
import com.israadev.nuxlauncher.ui.theme.LocalNuxScaled
import com.israadev.nuxlauncher.ui.theme.NuxReadable
import com.israadev.nuxlauncher.ui.theme.resp

/**
 * Kartu kaca: isian transparan tipis, tepi putih tipis (atau gradien lewat borderBrush), tanpa
 * bayangan. [shadowColor] dan [shadowOffset] dipertahankan supaya pemanggil lama tetap
 * dikompilasi, nilainya tidak dipakai lagi.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun NuxCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = NuxColors.SurfaceWhite,
    borderColor: Color = NuxColors.CardBorder,
    shadowColor: Color = NuxColors.Ink,
    shadowOffset: Dp = 0.dp,
    cornerRadius: Dp = (20.dp).resp(),
    borderWidth: Dp = NuxSizes.GlassBorder,
    borderBrush: Brush? = null,
    fillMaxHeight: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val cardModifier = if (fillMaxHeight) modifier.fillMaxSize() else modifier.fillMaxWidth()
    val shape = RoundedCornerShape(cornerRadius)
    val edge = if (borderBrush != null) {
        Modifier.border(borderWidth, borderBrush, shape)
    } else {
        Modifier.border(borderWidth, borderColor, shape)
    }

    Box(
        modifier = cardModifier
            .background(backgroundColor, shape)
            .then(edge)
            .clip(shape)
    ) {
        content()
    }
}

/**
 * Tombol AmberLauncher, gayanya dipilih otomatis dari warna isiannya:
 * - isian terang solid (kuning/putih): tombol utama, datar tanpa tepi dan tanpa bayangan, teks
 *   otomatis Ink supaya selalu terbaca;
 * - selain itu (kaca/transparan/gelap): tombol kaca, tepi putih tipis.
 * Umpan balik tekan digambar hanya di fase gambar (lapisan gelap/terang tipis), jadi menyentuh
 * tombol tidak menyusun ulang layar. [shadowOffset] dipertahankan supaya pemanggil lama tetap
 * dikompilasi, nilainya tidak dipakai lagi.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun NuxButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = NuxColors.ForestGreen,
    contentColor: Color = Color.Unspecified,
    borderColor: Color = Color.Unspecified,
    enabled: Boolean = true,
    shadowOffset: Dp = 0.dp,
    cornerRadius: Dp = (12.dp).resp(),
    contentPadding: PaddingValues = PaddingValues(horizontal = (14.dp).resp(), vertical = (6.dp).resp()),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressedState = interactionSource.collectIsPressedAsState()

    val filled = backgroundColor.alpha >= 0.9f && backgroundColor.luminance() > 0.45f
    val edgeColor = when {
        !enabled -> NuxColors.CardBorder
        borderColor != Color.Unspecified -> borderColor
        else -> NuxColors.CardBorder
    }
    val textColor = when {
        !enabled -> NuxColors.GrayNeutral.copy(alpha = 0.6f)
        filled && (contentColor == Color.Unspecified || contentColor.luminance() > 0.6f) -> NuxColors.Ink
        !filled && backgroundColor.alpha < 0.5f && contentColor != Color.Unspecified &&
            contentColor.luminance() < 0.05f -> NuxColors.DarkGray
        contentColor == Color.Unspecified -> NuxColors.DarkGray
        else -> contentColor
    }
    val shape = RoundedCornerShape(cornerRadius)
    val pressTint = if (filled) Color.Black.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.10f)
    val edge = if (filled && enabled) Modifier else Modifier.border(NuxSizes.GlassBorder, edgeColor, shape)

    Box(
        modifier = modifier
            .clip(shape)
            .background(if (enabled) backgroundColor else NuxColors.SurfaceWhite, shape)
            .then(edge)
            .drawWithContent {
                drawContent()
                if (enabled && pressedState.value) drawRect(pressTint)
            }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .defaultMinSize(minHeight = (40.dp).resp())
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides textColor
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                content()
            }
        }
    }
}

/**
 * Lencana kecil berbentuk pil dengan tepi kaca tipis.
 */
@Composable
fun NuxBadge(
    text: String,
    backgroundColor: Color = NuxColors.SoftLime,
    textColor: Color = NuxColors.DarkGray,
    borderColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape((8.dp).resp())
    val effectiveBorderColor = borderColor ?: NuxColors.CardBorder

    Box(
        modifier = modifier
            .background(backgroundColor, shape)
            .border(NuxSizes.GlassBorder, effectiveBorderColor, shape)
            .padding(horizontal = (10.dp).resp(), vertical = (4.dp).resp())
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = (11.sp).resp(),
            fontWeight = FontWeight.Bold,
            letterSpacing = (0.4.sp).resp(),
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Kolom isian kaca dengan tepi putih tipis, kursor kuning, tinggi nyaman untuk jari.
 */
@Composable
fun NuxTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle? = null,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape((14.dp).resp())
    val resolvedTextStyle = textStyle ?: TextStyle(
        color = NuxColors.DarkGray,
        fontSize = (14.sp).resp(),
        fontWeight = FontWeight.SemiBold
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(NuxColors.SurfaceInput, shape)
            .border(NuxSizes.GlassBorder, NuxColors.CardBorder, shape)
            .defaultMinSize(minHeight = (46.dp).resp())
            .padding(horizontal = (14.dp).resp(), vertical = (10.dp).resp()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = NuxColors.GrayNeutral.copy(alpha = 0.7f),
                    fontSize = (14.sp).resp(),
                    fontWeight = FontWeight.Normal
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = resolvedTextStyle,
                cursorBrush = SolidColor(NuxColors.Yellow),
                singleLine = true,
                visualTransformation = visualTransformation,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (trailingContent != null) {
            Spacer(modifier = Modifier.width((8.dp).resp()))
            trailingContent()
        }
    }
}

/**
 * Jendela dialog biasa (Dialog Compose) yang isinya otomatis memakai teks lebih besar (NuxReadable).
 * Dipakai oleh dialog yang menyusun tampilannya sendiri.
 */
@Composable
fun NuxWindowDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        NuxReadable(scale = 1f, boost = 1.18f) {
            content()
        }
    }
}

/**
 * Dialog kaca: panel gelap hampir buram dengan tepi gradien putih tipis di atas latar redup.
 * compact = true: dialog gaya baru (huruf sudah pas, ikut skala layar 0.9 seperti Akun/Mod/Pengaturan).
 * compact = false: dialog lama yang hurufnya kecil, diberi pembesar huruf (NuxReadable boost).
 */
@Composable
fun NuxDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    fillMaxHeight: Boolean = false,
    compact: Boolean = false,
    content: @Composable () -> Unit
) {
    val inScaledScreen = LocalNuxScaled.current
    val isTablet = LocalNuxScale.current.isTablet
    val defaultWidthFraction = if (isTablet) 0.82f else 0.96f
    val dialogEdge = remember {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.42f),
                Color.White.copy(alpha = 0.08f),
                Color.White.copy(alpha = 0.22f)
            )
        )
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        NuxReadable(
            scale = if (compact && !inScaledScreen) 0.9f else 1f,
            boost = if (compact) 1f else 1.18f
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = (12.dp).resp(), vertical = (8.dp).resp()),
                contentAlignment = Alignment.Center
            ) {
                val dialogModifier = if (fillMaxHeight) {
                    modifier.fillMaxWidth(defaultWidthFraction).fillMaxHeight(0.96f)
                } else {
                    modifier.fillMaxWidth(defaultWidthFraction).wrapContentHeight()
                }
                NuxCard(
                    modifier = dialogModifier,
                    backgroundColor = NuxColors.Panel,
                    borderBrush = dialogEdge,
                    cornerRadius = (24.dp).resp(),
                    fillMaxHeight = fillMaxHeight
                ) {
                    content()
                }
            }
        }
    }
}
