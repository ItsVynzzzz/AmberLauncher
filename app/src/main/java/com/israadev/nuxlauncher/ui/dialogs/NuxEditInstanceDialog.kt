package com.israadev.nuxlauncher.ui.dialogs

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.israadev.nuxlauncher.core.instance.InstanceManager
import com.israadev.nuxlauncher.core.models.Instance
import com.israadev.nuxlauncher.core.runtime.JavaRuntimeManager
import com.israadev.nuxlauncher.ui.components.NuxBadge
import com.israadev.nuxlauncher.ui.components.NuxButton
import com.israadev.nuxlauncher.ui.components.NuxCard
import com.israadev.nuxlauncher.ui.components.NuxWindowDialog
import com.israadev.nuxlauncher.ui.theme.LocalNuxScale
import com.israadev.nuxlauncher.ui.theme.NuxColors
import com.israadev.nuxlauncher.ui.theme.NuxSizes

@Composable
fun NuxEditInstanceDialog(
    instance: Instance,
    onDismiss: () -> Unit,
    onInstanceUpdated: (Instance) -> Unit,
    onInstanceDeleted: (Instance) -> Unit
) {
    val context = LocalContext.current
    val isTablet = LocalNuxScale.current.isTablet
    var editName by remember { mutableStateOf(instance.name) }
    var selectedJavaRuntime by remember { mutableStateOf(instance.javaRuntime) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val autoRecommendedRuntime = remember(instance.mcVersion) {
        JavaRuntimeManager.getRecommendedRuntime(instance.mcVersion)
    }

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
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            NuxCard(
                modifier = Modifier
                    .fillMaxWidth(if (isTablet) 0.62f else 0.80f)
                    .widthIn(min = 360.dp, max = 520.dp)
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
                    // ==========================================
                    // 1. TOP HEADER BAR (Double-Bezel Aura)
                    // ==========================================
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
                                            listOf(NuxColors.YellowPale, Color.Transparent)
                                        ),
                                        CircleShape
                                    )
                                    .border(1.2.dp, NuxColors.CardBorder, CircleShape)
                                    .padding(2.5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(NuxColors.SurfaceWhite, CircleShape)
                                        .border(1.dp, NuxColors.DarkGray.copy(alpha = 0.40f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Tune,
                                        contentDescription = null,
                                        tint = NuxColors.SageGreen,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Text(
                                    text = "INSTANCE CONFIGURATION",
                                    color = NuxColors.GrayNeutral,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 7.5.sp,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Edit Instance: ${instance.name}",
                                    color = NuxColors.DarkGray,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    letterSpacing = (-0.2).sp,
                                    maxLines = 1
                                )
                            }
                        }

                        // Close Button
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

                    // ==========================================
                    // 2. SCROLLABLE CONTENT (Double-Bezel Cards)
                    // ==========================================
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Section 1: Nama Instance (Double-Bezel)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(NuxColors.SurfaceWhite)
                                .border(1.dp, NuxColors.DarkGray.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = "NAMA INSTANCE",
                                color = NuxColors.GrayNeutral,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.sp,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(NuxColors.SurfaceInput)
                                    .border(1.dp, NuxColors.DarkGray.copy(alpha = 0.40f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 9.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                BasicTextField(
                                    value = editName,
                                    onValueChange = { editName = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = NuxColors.DarkGray,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    cursorBrush = SolidColor(NuxColors.Yellow),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Section 2: Custom Java Runtime Selection (Double-Bezel)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(NuxColors.SurfaceWhite)
                                .border(1.dp, NuxColors.DarkGray.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "JAVA RUNTIME",
                                    color = NuxColors.GrayNeutral,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.sp,
                                    letterSpacing = 0.8.sp
                                )
                                NuxBadge(
                                    text = "Rekomendasi: ${JavaRuntimeManager.getRuntimeDisplayName(autoRecommendedRuntime)}",
                                    backgroundColor = NuxColors.YellowPale,
                                    textColor = NuxColors.SageGreen,
                                    borderColor = NuxColors.CardBorder
                                )
                            }

                            Spacer(modifier = Modifier.height(5.dp))

                            // Runtimes Pills
                            val runtimeOptions = listOf(
                                "auto" to "Auto (${autoRecommendedRuntime.replace("jre-", "Java ")})",
                                "jre-8" to "Java 8",
                                "jre-17" to "Java 17",
                                "jre-21" to "Java 21",
                                "jre-25" to "Java 25"
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                runtimeOptions.forEach { (key, label) ->
                                    val isSelected = selectedJavaRuntime == key
                                    val pillBg by animateColorAsState(
                                        targetValue = if (isSelected) NuxColors.SoftLime else NuxColors.SurfaceInput,
                                        animationSpec = tween(150),
                                        label = "runtimeBg"
                                    )
                                    val pillBorder by animateColorAsState(
                                        targetValue = if (isSelected) NuxColors.MintGreen else NuxColors.DarkGray.copy(alpha = 0.13f),
                                        animationSpec = tween(150),
                                        label = "runtimeBorder"
                                    )

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(pillBg)
                                            .border(1.dp, pillBorder, RoundedCornerShape(6.dp))
                                            .clickable { selectedJavaRuntime = key }
                                            .padding(horizontal = 8.dp, vertical = 4.5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .background(NuxColors.MintGreen, CircleShape)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                            }
                                            Text(
                                                text = label,
                                                color = if (isSelected) NuxColors.SageGreen else NuxColors.GrayNeutral,
                                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Section 3: Target Engine (Bento Double-Bezel Card)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(NuxColors.SurfaceWhite)
                                .border(1.dp, NuxColors.DarkGray.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "TARGET ENGINE",
                                    color = NuxColors.GrayNeutral,
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "MC ${instance.mcVersion} · ${instance.loader.uppercase()}${if (instance.loaderVersion.isNotBlank()) " (${instance.loaderVersion})" else ""}",
                                    color = NuxColors.DarkGray,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 10.5.sp
                                )
                            }

                            // Interactive Buka Folder Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(NuxColors.SurfaceElevated)
                                    .border(1.dp, NuxColors.DarkGray.copy(alpha = 0.40f), RoundedCornerShape(6.dp))
                                    .clickable {
                                        Toast.makeText(context, "Membuka folder game...", Toast.LENGTH_SHORT).show()
                                        InstanceManager.openInstanceFolder(context, instance)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Folder,
                                        contentDescription = null,
                                        tint = NuxColors.SageGreen,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Buka Folder",
                                        color = NuxColors.DarkGray,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Section 4: Danger Zone (Hapus Instance)
                        if (!showDeleteConfirm) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(NuxColors.ErrorRed.copy(alpha = 0.12f))
                                    .border(1.dp, NuxColors.ErrorRed.copy(alpha = 0.35f), RoundedCornerShape(9.dp))
                                    .clickable { showDeleteConfirm = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = null,
                                            tint = NuxColors.ErrorRed,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Hapus Instance Ini",
                                            color = NuxColors.ErrorRed,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = "Hapus game & data permanen",
                                        color = NuxColors.GrayNeutral,
                                        fontSize = 8.sp
                                    )
                                }
                            }
                        } else {
                            // Confirm Delete Box
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(NuxColors.ErrorRed.copy(alpha = 0.12f))
                                    .border(1.dp, NuxColors.ErrorRed.copy(alpha = 0.7f), RoundedCornerShape(9.dp))
                                    .padding(9.dp)
                            ) {
                                Text(
                                    text = "Yakin ingin menghapus '${instance.name}'?",
                                    color = NuxColors.DarkGray,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Semua world, mod, config, dan file instance akan dihapus permanen.",
                                    color = NuxColors.ErrorRed,
                                    fontSize = 8.5.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    NuxButton(
                                        onClick = { showDeleteConfirm = false },
                                        backgroundColor = NuxColors.Panel,
                                        borderColor = NuxColors.CardBorder,
                                        contentColor = NuxColors.DarkGray,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(28.dp)
                                    ) {
                                        Text("BATAL", color = NuxColors.DarkGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }

                                    NuxButton(
                                        onClick = {
                                            onInstanceDeleted(instance)
                                            onDismiss()
                                        },
                                        backgroundColor = NuxColors.ErrorRed,
                                        borderColor = NuxColors.ErrorRed.copy(alpha = 0.27f),
                                        contentColor = NuxColors.DarkGray,
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .height(28.dp)
                                    ) {
                                        Text("YA, HAPUS SEKARANG", color = NuxColors.DarkGray, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // ==========================================
                    // 3. BOTTOM ACTION BAR (High Contrast)
                    // ==========================================
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // BATAL BUTTON (Dark Obsidian Glass + High-contrast White Text)
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
                                letterSpacing = 0.5.sp
                            )
                        }

                        // SIMPAN PERUBAHAN BUTTON (Vibrant Emerald Gradient + Crisp White Text)
                        NuxButton(
                            onClick = {
                                val trimmed = editName.trim()
                                if (trimmed.isBlank()) {
                                    Toast.makeText(context, "Nama instance tidak boleh kosong", Toast.LENGTH_SHORT).show()
                                    return@NuxButton
                                }
                                val updated = instance.copy(
                                    name = trimmed,
                                    javaRuntime = selectedJavaRuntime
                                )
                                onInstanceUpdated(updated)
                                Toast.makeText(context, "Instance berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            backgroundColor = NuxColors.ForestGreen,
                            borderColor = NuxColors.Ink,
                            contentColor = NuxColors.DarkGray,
                            modifier = Modifier
                                .weight(1.5f)
                                .height(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = NuxColors.Ink,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "SIMPAN PERUBAHAN",
                                color = NuxColors.Ink,
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
