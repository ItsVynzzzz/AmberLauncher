package com.israadev.nuxlauncher.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.models.Instance
import com.israadev.nuxlauncher.core.mods.ModUpdate
import com.israadev.nuxlauncher.core.mods.ModUpdateCheck
import com.israadev.nuxlauncher.core.mods.ModUpdater
import com.israadev.nuxlauncher.ui.components.AmberActionButton
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.theme.AmberUi
import com.israadev.nuxlauncher.ui.theme.NuxColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class ModUpdatePhase { IDLE, CHECKING, APPLYING }

/**
 * Keadaan tombol "Perbarui semua mod" di Manajemen Mod. Semua pekerjaan berat (hash, jaringan, unduhan)
 * berjalan di [ModUpdater] pada utas IO; kelas ini hanya memegang keadaan yang tampil di layar.
 */
@Stable
class ModUpdateState {
    var phase by mutableStateOf(ModUpdatePhase.IDLE)
        private set

    /** Hasil pemeriksaan terakhir yang masih berlaku; null = belum diperiksa atau sudah usang. */
    var check by mutableStateOf<ModUpdateCheck?>(null)
        private set

    /** Pesan hasil terakhir (sudah dalam bahasa Indonesia, diterjemahkan oleh Text di layar). */
    var note by mutableStateOf<String?>(null)
        private set
    var isError by mutableStateOf(false)
        private set
    var done by mutableIntStateOf(0)
        private set
    var total by mutableIntStateOf(0)
        private set
    var current by mutableStateOf("")
        private set

    private var job: Job? = null

    val busy: Boolean get() = phase != ModUpdatePhase.IDLE
    val pendingCount: Int get() = check?.updates?.size ?: 0

    /** Nama berkas (di disk) dari mod yang punya pembaruan, untuk lencana di daftar. */
    fun updateFor(fileName: String): ModUpdate? = check?.updates?.firstOrNull { it.fileName == fileName }

    /**
     * Memeriksa pembaruan. Bila [applyAfterCheck], pembaruan yang ditemukan langsung dipasang (mode otomatis).
     * [onChanged] dipanggil setelah folder mods berubah supaya daftar dimuat ulang.
     */
    fun startCheck(context: Context, instance: Instance, scope: CoroutineScope, applyAfterCheck: Boolean, onChanged: () -> Unit) {
        if (busy) return
        val appContext = context.applicationContext
        phase = ModUpdatePhase.CHECKING
        note = null
        isError = false
        job = scope.launch {
            try {
                val result = ModUpdater.check(appContext, instance)
                val found = result.getOrNull()
                if (found == null) {
                    check = null
                    isError = true
                    note = failureText(result.exceptionOrNull())
                } else {
                    check = found
                    if (applyAfterCheck && found.updates.isNotEmpty()) applyInternal(appContext, instance, found, onChanged)
                }
            } catch (e: CancellationException) {
                check = null
                note = "Pembaruan dibatalkan"
                throw e
            } finally {
                phase = ModUpdatePhase.IDLE
            }
        }
    }

    /** Memasang semua pembaruan dari pemeriksaan terakhir. */
    fun startApply(context: Context, instance: Instance, scope: CoroutineScope, onChanged: () -> Unit) {
        val found = check ?: return
        if (busy || found.updates.isEmpty()) return
        val appContext = context.applicationContext
        // Diatur sebelum coroutine mulai, supaya ketukan kedua yang datang cepat tidak memulai pekerjaan ganda
        phase = ModUpdatePhase.APPLYING
        job = scope.launch {
            try {
                applyInternal(appContext, instance, found, onChanged)
            } finally {
                phase = ModUpdatePhase.IDLE
            }
        }
    }

    private suspend fun applyInternal(context: Context, instance: Instance, found: ModUpdateCheck, onChanged: () -> Unit) {
        phase = ModUpdatePhase.APPLYING
        done = 0
        total = found.updates.size
        current = ""
        note = null
        isError = false
        try {
            val result = ModUpdater.apply(context, instance, found.updates) { d, t, name ->
                done = d
                total = t
                current = name
            }
            // Yang gagal tetap tercatat supaya bisa dicoba lagi tanpa memeriksa ulang
            check = found.copy(updates = result.failed)
            note = if (result.failed.isEmpty()) "${result.updated.size} mod berhasil diperbarui"
            else "${result.updated.size} mod diperbarui, ${result.failed.size} gagal"
            isError = result.failed.isNotEmpty()
            onChanged()
        } catch (e: CancellationException) {
            // Mod yang sempat selesai tetap terpasang, jadi hasil pemeriksaan lama tidak berlaku lagi
            check = null
            note = "Pembaruan dibatalkan"
            onChanged()
            throw e
        }
    }

    fun cancel() {
        job?.cancel()
    }

    /** Menganggap hasil pemeriksaan usang bila ada berkas yang berubah di luar pembaruan (diaktif/dinonaktifkan, dihapus). */
    fun reconcile(existingFileNames: Set<String>) {
        if (phase != ModUpdatePhase.IDLE) return
        val c = check ?: return
        if (c.updates.any { it.fileName !in existingFileNames }) {
            check = null
            note = null
            isError = false
        }
    }

    private fun failureText(error: Throwable?): String {
        val offline = error is java.net.UnknownHostException ||
            error is java.net.ConnectException ||
            error is java.net.SocketTimeoutException
        return if (offline) "Tidak ada koneksi ke Modrinth. Periksa internet lalu coba lagi."
        else "Gagal memeriksa pembaruan: ${error?.message ?: "galat tidak diketahui"}"
    }
}

/**
 * Bar di atas daftar mod terpasang: periksa pembaruan, perbarui semua sekaligus, dan sakelar mode otomatis.
 */
@Composable
internal fun ModUpdateBar(
    state: ModUpdateState,
    autoEnabled: Boolean,
    onCheck: () -> Unit,
    onApplyAll: () -> Unit,
    onCancel: () -> Unit,
    onAutoChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    val phase = state.phase
    val check = state.check
    val pending = state.pendingCount

    val title: String = when {
        phase == ModUpdatePhase.CHECKING -> "Memeriksa pembaruan mod..."
        phase == ModUpdatePhase.APPLYING ->
            "Memperbarui ${minOf(state.done + 1, state.total)}/${state.total}: ${state.current}"
        state.note != null -> state.note.orEmpty()
        check != null && pending > 0 -> "$pending mod punya versi baru"
        check != null -> "Semua mod sudah terbaru"
        autoEnabled -> "Pembaruan otomatis aktif"
        else -> "Cek versi baru semua mod lewat Modrinth"
    }
    val detail: String? = if (phase == ModUpdatePhase.IDLE && check != null) {
        when {
            check.skipped.isNotEmpty() ->
                "${check.skipped.size} mod dilewati (butuh dependensi baru atau bentrok), perbarui manual"
            check.unknown > 0 -> "${check.unknown} mod tidak ada di Modrinth, tidak bisa dicek"
            else -> null
        }
    } else {
        null
    }
    val icon = when {
        state.isError && phase == ModUpdatePhase.IDLE -> Icons.Outlined.WarningAmber
        phase == ModUpdatePhase.IDLE && check != null && pending == 0 -> Icons.Outlined.Check
        else -> Icons.Outlined.Download
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AmberUi.SurfaceRaised, shape)
            .border(1.dp, AmberUi.Border, shape)
            .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (state.isError && phase == ModUpdatePhase.IDLE) AmberUi.Danger else AmberUi.AccentSoft,
            modifier = Modifier.size(20.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = AmberUi.Text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (detail != null) {
                Text(
                    text = detail,
                    color = AmberUi.TextDim,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (phase == ModUpdatePhase.APPLYING && state.total > 0) {
                Spacer(modifier = Modifier.height(5.dp))
                val fraction = (state.done.toFloat() / state.total.toFloat()).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(AmberUi.Border)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(AmberUi.Accent)
                    )
                }
            }
        }

        // Sakelar mode otomatis
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onAutoChange(!autoEnabled) }
                .padding(start = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Otomatis",
                color = AmberUi.TextDim,
                fontSize = 12.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(2.dp))
            Switch(
                checked = autoEnabled,
                onCheckedChange = { onAutoChange(it) },
                modifier = Modifier.scale(0.78f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AmberUi.Accent,
                    checkedBorderColor = AmberUi.AccentEdge,
                    uncheckedThumbColor = AmberUi.TextDim,
                    uncheckedTrackColor = NuxColors.SurfaceElevated
                )
            )
        }

        when {
            phase == ModUpdatePhase.APPLYING -> AmberActionButton(
                text = "Batal",
                onClick = onCancel,
                filled = false,
                height = 38.dp
            )
            phase == ModUpdatePhase.CHECKING -> AmberActionButton(
                text = "Memeriksa...",
                onClick = {},
                loading = true,
                filled = false,
                height = 38.dp
            )
            pending > 0 -> AmberActionButton(
                text = "Perbarui semua ($pending)",
                onClick = onApplyAll,
                height = 38.dp
            )
            else -> AmberActionButton(
                text = "Periksa pembaruan",
                onClick = onCheck,
                filled = false,
                height = 38.dp
            )
        }
    }
}
