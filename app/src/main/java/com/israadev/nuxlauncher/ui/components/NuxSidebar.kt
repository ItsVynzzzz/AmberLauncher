package com.israadev.nuxlauncher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import com.israadev.nuxlauncher.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.R
import com.israadev.nuxlauncher.ui.theme.NuxColors

data class NuxNavItem(
    val id: String,
    val icon: ImageVector,
    val contentDescription: String
)

val NUX_NAV_ITEMS = listOf(
    NuxNavItem("home", Icons.Outlined.Home, "Home"),
    NuxNavItem("accounts", Icons.Outlined.Person, "Akun"),
    NuxNavItem("mods", Icons.Outlined.Extension, "Mod"),
    NuxNavItem("settings", Icons.Outlined.Settings, "Pengaturan")
)

/**
 * Rel navigasi penuh tinggi di tepi kiri layar. Tanpa kotak sendiri: ia berdiri langsung di atas
 * latar hitam layar, jadi menyatu dengan kartu panel di sebelahnya.
 * [startInset] = lebar area kamera/poni di sisi kiri: rel diperlebar sebesar itu
 * sehingga isi rel tetap berada di luar poni.
 *
 * Atas: logo. Tengah: 4 menu (ikon + label, indikator kuning untuk menu aktif).
 * Bawah: tombol info (Tentang & Lisensi).
 */
@Composable
fun NuxSidebar(
    activeTab: String,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    startInset: Dp = 0.dp,
    onOpenAbout: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .width(startInset + 68.dp)
            .fillMaxHeight()
    ) {
        Box(
            modifier = Modifier
                .padding(start = startInset)
                .width(68.dp)
                .fillMaxHeight()
        ) {
            // Logo (ketuk = kembali ke Home)
            val logoShape = RoundedCornerShape(12.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .size(40.dp)
                    .clip(logoShape)
                    .clickable { onTabSelected("home") },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.nux_icon),
                    contentDescription = "Logo",
                    modifier = Modifier.size(40.dp)
                )
            }

            // Menu utama di tengah
            Column(
                modifier = Modifier.align(Alignment.Center),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NUX_NAV_ITEMS.forEach { item ->
                    val isSelected = activeTab == item.id
                    Column(
                        modifier = Modifier
                            .width(62.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onTabSelected(item.id) }
                            .padding(vertical = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 52.dp, height = 34.dp)
                                .background(
                                    color = if (isSelected) NuxColors.Yellow else Color.Transparent,
                                    shape = RoundedCornerShape(17.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.contentDescription,
                                tint = if (isSelected) NuxColors.Ink else Color.White.copy(alpha = 0.72f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.contentDescription,
                            color = if (isSelected) NuxColors.Yellow else Color.White.copy(alpha = 0.72f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }

            // Info: Tentang & Lisensi
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onOpenAbout() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Tentang & Lisensi",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
