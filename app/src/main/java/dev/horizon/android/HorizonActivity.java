package dev.horizon.android;

import android.app.NativeActivity;
import android.content.Intent;
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

    private native void nativeDocumentResult(int request, int result, String uri);

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
