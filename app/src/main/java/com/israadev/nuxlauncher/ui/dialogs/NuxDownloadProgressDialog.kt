package com.israadev.nuxlauncher.ui.dialogs

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.ui.components.NuxDialog
import com.israadev.nuxlauncher.ui.theme.NuxColors

@Composable
fun NuxDownloadProgressDialog(
    instanceName: String,
    progress: Float,
    message: String,
    onDismissRequest: () -> Unit = {}
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 250),
        label = "dlProgress"
    )

    val outerShape = RoundedCornerShape(22.dp)
    val cardShape = RoundedCornerShape(14.dp)

    NuxDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxWidth(0.62f)
    ) {
        // Outer Shell Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(outerShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            NuxColors.SurfaceElevated,
                            NuxColors.SurfaceInput
                        )
                    )
                )
                .border(1.dp, NuxColors.CardBorder, outerShape)
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(NuxColors.YellowPale)
                                .border(1.dp, NuxColors.CardBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = NuxColors.SageGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "CORE ASSET PIPELINE",
                                color = NuxColors.GrayNeutral,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.5.sp,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                text = "Mengunduh Game",
                                color = NuxColors.DarkGray,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                letterSpacing = (-0.3).sp
                            )
                        }
                    }

                    // Percentage Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(NuxColors.YellowPale)
                            .border(1.dp, NuxColors.CardBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${(animatedProgress * 100).toInt()}%",
                            color = NuxColors.SageGreen,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Instance Name Badge Core
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(cardShape)
                        .background(NuxColors.SurfaceInput)
                        .border(1.dp, NuxColors.DarkGray.copy(alpha = 0.20f), cardShape)
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(NuxColors.ForestGreen, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = instanceName,
                                color = NuxColors.DarkGray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            text = "MOJANG ASSETS",
                            color = NuxColors.GrayNeutral,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 9.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // High-End Cyber Gradient Progress Track (Double Bezel)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(CircleShape)
                        .background(NuxColors.Background, CircleShape)
                        .border(1.dp, NuxColors.DarkGray.copy(alpha = 0.36f), CircleShape)
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = animatedProgress)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        NuxColors.ForestGreen,
                                        NuxColors.ForestGreen,
                                        NuxColors.ForestGreen,
                                        NuxColors.SkyBlue
                                    )
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Current Action / File Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = NuxColors.SageGreen
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = message.ifBlank { "Menyiapkan dependensi game & checksum..." },
                        color = NuxColors.GrayNeutral,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.5.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
