package com.israadev.nuxlauncher.ui.screens

import com.israadev.nuxlauncher.ui.theme.amberGlass
import com.israadev.nuxlauncher.ui.theme.AmberStyle
import com.israadev.nuxlauncher.ui.theme.AmberGlassKind
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.models.UserAccount
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.theme.AmberUi

/**
 * Menu akun cepat yang muncul di bawah pil pemain Home: ganti akun dengan satu ketukan,
 * hapus akun, dan tambah akun baru tanpa harus membuka tab Akun.
 * Akun yang sedang dipakai diberi garis tepi biru.
 */
@Composable
internal fun HomeAccountMenu(
    accounts: List<UserAccount>,
    currentAccountId: String?,
    onSelect: (UserAccount) -> Unit,
    onDelete: (UserAccount) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Daftar akun (bisa digulir bila banyak)
        Column(
            modifier = Modifier
                .heightIn(max = 190.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            accounts.forEach { acc ->
                val isCurrent = acc.id == currentAccountId
                val shape = RoundedCornerShape(24.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .glassOrCard(shape)
                        .then(
                            if (isCurrent) Modifier.border(1.5.dp, AmberUi.AccentEdge, shape) else Modifier
                        )
                        .clickable { onSelect(acc) }
                        .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NuxAccountAvatar(
                        account = acc,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = acc.username,
                        color = AmberUi.Text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable { onDelete(acc) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Hapus akun",
                            tint = AmberUi.Danger,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // Tambah akun
        val addShape = RoundedCornerShape(24.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(addShape)
                .glassOrCard(addShape)
                .clickable { onAdd() }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    tint = AmberUi.Text,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "Tambah akun",
                color = AmberUi.Text,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                maxLines = 1
            )
        }
    }
}

/** Kartu menu: kaca saat gaya Liquid Glass, kartu gelap pekat saat gaya Default. */
private fun Modifier.glassOrCard(shape: androidx.compose.ui.graphics.Shape): Modifier =
    if (AmberUi.style == AmberStyle.LIQUID) {
        amberGlass(shape, AmberGlassKind.Home)
    } else {
        this
            .background(AmberUi.MenuCard, shape)
            .border(1.dp, AmberUi.Border, shape)
    }
