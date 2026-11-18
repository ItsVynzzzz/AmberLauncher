package com.israadev.nuxlauncher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text as RawText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.update.WhatsNew
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.i18n.Tr
import com.israadev.nuxlauncher.ui.theme.Amber

/**
 * Daftar butir "Yang baru" dalam bahasa launcher, dikelompokkan: fitur baru, peningkatan, perbaikan.
 * Tinggi daftar mengikuti isinya; pemanggil yang membatasi tinggi (dialog) membungkusnya dengan gulir.
 */
@Composable
fun WhatsNewList(
    notes: WhatsNew.Notes,
    modifier: Modifier = Modifier,
    textColor: Color = Amber.TextDim,
    headingColor: Color = Amber.Yellow,
    fontSize: TextUnit = 13.sp
) {
    // Dibaca saat digambar: bahasa yang diganti membuat seluruh layar digambar ulang (lihat AmberLanguageHost).
    val items = notes.forLanguage(Tr.effective)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (kind in WhatsNew.Kind.entries) {
            val group = items.filter { it.kind == kind }
            if (group.isEmpty()) continue
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = when (kind) {
                        WhatsNew.Kind.NEW -> "Fitur baru"
                        WhatsNew.Kind.IMPROVED -> "Peningkatan"
                        WhatsNew.Kind.FIXED -> "Perbaikan"
                    },
                    color = headingColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                for (item in group) {
                    Row {
                        RawText(text = "•", color = textColor, fontSize = fontSize)
                        Spacer(modifier = Modifier.width(8.dp))
                        // Teks butir sudah dalam bahasa pilihan: dipakai apa adanya, tanpa kamus terjemahan.
                        RawText(
                            text = item.text,
                            color = textColor,
                            fontSize = fontSize,
                            lineHeight = fontSize * 1.45f,
                            modifier = Modifier.weight(1f).padding(bottom = 1.dp)
                        )
                    }
                }
            }
        }
    }
}
