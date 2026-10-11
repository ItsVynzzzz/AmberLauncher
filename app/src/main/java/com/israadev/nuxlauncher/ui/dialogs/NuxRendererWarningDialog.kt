package com.israadev.nuxlauncher.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.WarningAmber
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
import com.israadev.nuxlauncher.core.renderer.NuxRendererInfo
import com.israadev.nuxlauncher.ui.components.NuxButton
import com.israadev.nuxlauncher.ui.components.NuxCard
import com.israadev.nuxlauncher.ui.components.NuxWindowDialog
import com.israadev.nuxlauncher.ui.theme.LocalNuxScale
import com.israadev.nuxlauncher.ui.theme.NuxColors

@Composable
fun NuxRendererWarningDialog(
    renderer: NuxRendererInfo,
    mcVersion: String,
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
                    .fillMaxWidth(if (isTablet) 0.54f else 0.70f)
                    .widthIn(min = 340.dp, max = 480.dp)
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
                                            listOf(NuxColors.Amber.copy(alpha = 0.28f), Color.Transparent)
                                        ),
                                        CircleShape
                                    )
                                    .border(1.2.dp, NuxColors.Amber.copy(alpha = 0.6f), CircleShape)
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
                                        imageVector = Icons.Outlined.WarningAmber,
                                        contentDescription = null,
                                        tint = NuxColors.AmberDark,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Text(
                                    text = "PERINGATAN PERENDER",
                                    color = NuxColors.AmberDark,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 7.5.sp,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Inkompatibilitas Render",
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

                    // Warning Content Box (Double-Bezel)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(NuxColors.SurfaceWhite)
                            .border(1.dp, NuxColors.Amber.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                            .padding(11.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Perender saat ini \"${renderer.displayName}\" tidak didukung resmi untuk Minecraft $mcVersion.",
                                color = NuxColors.DarkGray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                lineHeight = 15.sp
                            )
                            Text(
                                text = "Memaksakan render ini dapat mengakibatkan visual artifact, freeze layar, atau crash. Apakah Anda yakin ingin melanjutkan?",
                                color = NuxColors.GrayNeutral,
                                fontSize = 9.sp,
                                lineHeight = 13.5.sp
                            )
                            if (renderer.compatibility.isNotBlank()) {
                                Text(
                                    text = "Dukungan resmi: ${renderer.compatibility}",
                                    color = NuxColors.AmberDark,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action Buttons
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
                                color = NuxColors.DarkGray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 0.4.sp
                            )
                        }

                        NuxButton(
                            onClick = onConfirm,
                            backgroundColor = NuxColors.Amber,
                            borderColor = NuxColors.Amber.copy(alpha = 0.40f),
                            contentColor = NuxColors.DarkGray.copy(alpha = 0.25f),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(34.dp)
                        ) {
                            Text(
                                text = "TETAP JALANKAN",
                                color = NuxColors.DarkGray,
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp,
                                letterSpacing = 0.4.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
