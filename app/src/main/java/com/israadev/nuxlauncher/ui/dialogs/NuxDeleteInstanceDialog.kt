package com.israadev.nuxlauncher.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.israadev.nuxlauncher.core.models.Instance
import com.israadev.nuxlauncher.ui.components.NuxButton
import com.israadev.nuxlauncher.ui.components.NuxCard
import com.israadev.nuxlauncher.ui.components.NuxWindowDialog
import com.israadev.nuxlauncher.ui.theme.LocalNuxScale
import com.israadev.nuxlauncher.ui.theme.NuxColors

@Composable
fun NuxDeleteInstanceDialog(
    instance: Instance,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isTablet = LocalNuxScale.current.isTablet

    NuxWindowDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            NuxCard(
                modifier = Modifier
                    .fillMaxWidth(if (isTablet) 0.52f else 0.68f)
                    .widthIn(min = 340.dp, max = 460.dp)
                    .wrapContentHeight(),
                backgroundColor = NuxColors.Panel,
                borderColor = NuxColors.CardBorder,
                cornerRadius = 18.dp,
                fillMaxHeight = false
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Bezel icon ring
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(NuxColors.ErrorRed.copy(alpha = 0.28f), Color.Transparent)
                                        ),
                                        CircleShape
                                    )
                                    .border(1.2.dp, NuxColors.ErrorRed.copy(alpha = 0.6f), CircleShape)
                                    .padding(2.5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(NuxColors.ErrorRed.copy(alpha = 0.12f), CircleShape)
                                        .border(1.dp, NuxColors.DarkGray.copy(alpha = 0.40f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteForever,
                                        contentDescription = null,
                                        tint = NuxColors.ErrorRed,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Text(
                                    text = "KONFIRMASI TINDAKAN",
                                    color = NuxColors.ErrorRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 7.5.sp,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Hapus Instance",
                                    color = NuxColors.DarkGray,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    letterSpacing = (-0.2).sp
                                )
                            }
                        }

                        // Close button
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(NuxColors.SurfaceWhite, CircleShape)
                                .border(1.dp, NuxColors.DarkGray.copy(alpha = 0.40f), CircleShape)
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Tutup",
                                tint = NuxColors.GrayNeutral,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Instance Information Card (Double-Bezel)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(NuxColors.SurfaceWhite)
                            .border(1.dp, NuxColors.DarkGray.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = instance.name,
                                color = NuxColors.DarkGray,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Versi: ${instance.mcVersion} · Loader: ${instance.loader.uppercase()}${if (instance.loaderVersion.isNotBlank()) " (${instance.loaderVersion})" else ""}",
                                color = NuxColors.GrayNeutral,
                                fontWeight = FontWeight.Medium,
                                fontSize = 9.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Warning explanation
                    Text(
                        text = "Apakah Anda yakin ingin menghapus instance ini? Semua dunia game, file modifikasi, savegame, dan konfigurasi akan dihapus secara permanen.",
                        color = NuxColors.GrayNeutral,
                        fontSize = 9.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Normal
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NuxButton(
                            onClick = onDismiss,
                            backgroundColor = NuxColors.Panel,
                            borderColor = NuxColors.CardBorder,
                            contentColor = NuxColors.DarkGray,
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                        ) {
                            Text(
                                text = "BATAL",
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = NuxColors.DarkGray,
                                letterSpacing = 0.4.sp
                            )
                        }

                        NuxButton(
                            onClick = onConfirm,
                            backgroundColor = NuxColors.ErrorRed,
                            borderColor = NuxColors.ErrorRed.copy(alpha = 0.27f),
                            contentColor = NuxColors.DarkGray,
                            modifier = Modifier
                                .weight(1.3f)
                                .height(34.dp)
                        ) {
                            Text(
                                text = "HAPUS INSTANCE 🗑",
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp,
                                color = NuxColors.DarkGray,
                                letterSpacing = 0.4.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
