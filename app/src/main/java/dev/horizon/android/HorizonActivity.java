package dev.horizon.android;

import android.app.NativeActivity;
import android.content.Intent;
import android.os.Bundle;
import com.israadev.nuxlauncher.BuildConfig;
import com.israadev.nuxlauncher.discord.DiscordPresence;
import java.io.File;

/**
 * Pintu masuk mesin Rust (Horizon) di dalam AmberLauncher. NativeActivity memuat libhorizon_android.so
 * (nama pustaka ada di AndroidManifest.xml, meta-data android.app.lib_name) dan seluruh layar, input, serta
 * berkasnya diurus kode Rust. Kelas ini hanya meneruskan hasil pemilih berkas Android (SAF) ke Rust.
 *
 * PAKET DAN NAMA KELAS TIDAK BOLEH DIUBAH: fungsi JNI di pustaka native terikat padanya.
 *
 * Berdasarkan HorizonActivity dari Horizon Android (https://github.com/Vasysik/horizon-android), MIT,
 * Copyright (c) 2026 Ludovic Marie and the Horizon contributors. Teks lisensi: third_party/horizon/LICENSE.
 * Perubahan di AmberLauncher: getFilesDir() dan getCacheDir() diarahkan ke subfolder "horizon".
 */
public final class HorizonActivity extends NativeActivity {
    static { System.loadLibrary("horizon_android"); }

    /** Diisi launcher: true bila pemain menyalakan Discord Rich Presence. Kunci ini dibaca di proses :horizon. */
    public static final String EXTRA_DISCORD_RPC = "amber_discord_rpc";
    /** Versi Minecraft yang ditampilkan di Rich Presence. */
    public static final String EXTRA_MC_VERSION = "amber_mc_version";

    private native void nativeDocumentResult(int request, int result, String uri);

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startDiscordPresence();
    }

    @Override protected void onDestroy() {
        try {
            if (BuildConfig.HAS_DISCORD_SDK) DiscordPresence.INSTANCE.stopBlocking(800L);
        } catch (Throwable ignored) {
            // Presence hanyalah pelengkap: apa pun yang terjadi, mesin harus tetap bisa ditutup.
        }
        super.onDestroy();
    }

    // Discord Rich Presence (opsional): hanya pada build yang membawa SDK Discord dan bila pemain menyalakannya
    // (launcher menitipkan pilihan itu lewat Intent, jadi proses :horizon tidak perlu membaca pengaturan).
    // Mesin memanggil process::exit saat selesai, jadi onDestroy tidak selalu sempat jalan; Discord sendiri
    // menghapus presence begitu prosesnya mati.
    private void startDiscordPresence() {
        try {
            if (!BuildConfig.HAS_DISCORD_SDK) return;
            Intent intent = getIntent();
            if (intent == null || !intent.getBooleanExtra(EXTRA_DISCORD_RPC, false)) return;
            String version = intent.getStringExtra(EXTRA_MC_VERSION);
            DiscordPresence rpc = DiscordPresence.INSTANCE;
            rpc.attach(this);
            rpc.update(rpc.gamePresence(version == null ? "" : version, "horizon", System.currentTimeMillis()));
        } catch (Throwable ignored) {
            // Gagal menyalakan presence tidak boleh mengganggu mesin.
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 4101) return;
        nativeDocumentResult(request, result,
                data == null || data.getData() == null ? null : data.getData().toString());
    }

    // NativeActivity membaca getFilesDir() dan getCacheDir() saat dibuat, lalu mesin memakainya sebagai
    // rumah datanya (dunia, profil, sumber daya, log). Subfolder memisahkannya dari data launcher.
    @Override public File getFilesDir() { return subfolder(super.getFilesDir()); }

    @Override public File getCacheDir() { return subfolder(super.getCacheDir()); }

    private static File subfolder(File base) {
        File dir = new File(base, "horizon");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }
}
