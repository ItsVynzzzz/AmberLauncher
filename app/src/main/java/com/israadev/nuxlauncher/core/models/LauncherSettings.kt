package com.israadev.nuxlauncher.core.models

/**
 * Global Configuration for NUX Launcher (Java, RAM, Controls, Graphics)
 */
data class LauncherSettings(
    // JVM & RAM Memory
    val ramMb: Int = 1536,
    val initialHeapMb: Int = 256,
    val customJvmArgs: String = "",
    val defaultJavaRuntime: String = "auto",

    // In-game Mouse & Touch Controls (Moved from in-game HUD to Settings)
    val mouseControlMode: String = "SLIDE", // "SLIDE" (Trackpad) or "CLICK" (Direct Touch)
    val cursorSensitivity: Int = 100, // 50% - 300%
    val captureSensitivity: Int = 125, // 50% - 300% (Camera look speed)
    // Pukul/hancurkan lewat ketukan atau tahan di layar kosong saat bermain. Bawaan mati supaya
    // menggeser kamera tidak ikut memukul; pukul lewat tombol kontrol tetap bekerja.
    val touchAttackEnabled: Boolean = false,
    // Hotbar sentuh gaya Bedrock: ketuk hotbar game untuk memilih slot saat bermain.
    val hotbarTouchEnabled: Boolean = true,
    // Bar kecil di atas keyboard layar yang menampilkan huruf yang sedang diketik (keyboard menutupi kotak chat game).
    val typingPreview: Boolean = true,
    // Menggeser jari dari tombol serang/pakai/lompat ikut memutar kamera. Bawaan mati: jari yang
    // sedikit bergeser saat menahan tombol (PvP) tidak boleh menggeser pandangan.
    val buttonLookDrag: Boolean = false,
    // Eksperimental, bawaan mati. Bila modnya terpasang dan tersambung, sentuhan juga diteruskan ke mod
    // sementara tombol dan mouse virtual launcher tetap aktif. Namanya sengaja baru supaya nilai "aktif"
    // dari versi sebelumnya tidak terbawa.
    val touchControllerEnabled: Boolean = false,
    val mouseSizeDp: Int = 24, // 16 - 48 dp
    // Mesin Rust: tombol layar mengikuti tata letak kontrol Amber (bukan kontrol bawaan mesin). Diterapkan saat PLAY.
    val rustAmberControls: Boolean = true,
    // Mesin Rust: tombol bergaris tiga di layar game yang membuka menu cepat mesin. Bawaan nyala. Diterapkan saat PLAY.
    val rustControlHub: Boolean = true,
    val hideMouseInClickMode: Boolean = true,

    // Graphics & Performance
    val resolutionRatio: Int = 100, // 40% - 125%, kelipatan 5 (slider di Pengaturan dan menu dalam game)
    val autoOptimizeMinecraft: Boolean = true,
    val sustainedPerformanceMode: Boolean = false,
    val selectedRenderer: String = "auto", // "auto", "krypton", "mobileglues", "gl4es", "zink", "freedreno"
    val vulkanDriver: String = "auto", // "auto", "system", "turnip", etc.
    val graphicsApi: String = "DEFAULT", // "DEFAULT", "OPENGL", "VULKAN"
    val zinkPreferSystemDriver: Boolean = false,
    val vsyncInZink: Boolean = false,

    // Rekam layar bawaan (encoder perangkat keras). Bawaan sengaja hemat: 720p, 30 fps, tanpa mikrofon.
    val recordQuality: String = "light", // "eco" (540p), "light" (720p), "high" (1080p)
    val recordFps: Int = 30, // 30 atau 60
    val recordMic: Boolean = false, // rekam suara mikrofon (butuh izin)
    val recordThermalGuard: Boolean = true, // hentikan rekaman otomatis bila HP sangat panas
    val recordWithControls: Boolean = false, // false = layar game saja (tombol disembunyikan saat merekam), true = ikut merekam tombol kontrol

    // Plugin native library (APK format Zalith/FCL, mis. untuk Distant Horizons) yang DIMATIKAN pengguna,
    // berupa nama paket. Daftar kosong = semua plugin terpasang dipakai. Menyimpan yang dimatikan (bukan yang
    // dinyalakan) membuat plugin baru langsung aktif tanpa langkah tambahan.
    val disabledNativePlugins: List<String> = emptyList(),

    // Kunci layar 90/120 Hz ke 60 Hz selama game berjalan (bawaan mati).
    val lockRefresh60: Boolean = false,
    // HP RAM kecil: "auto" (bawaan launcher), "serial", atau "default"
    // (tanpa flag GC tambahan dari launcher).
    val generalGcMode: String = "auto",

    // MobileGlues: launcher memberi folder config sendiri (MG_DIR_PATH) supaya MobileGlues membaca config.json
    // dan cache terjemahan shader menyala. Mati = MobileGlues memakai bawaan paksanya (tanpa cache).
    val mobileGluesConfig: Boolean = true,

    // Mode Hemat: preset sekali ketuk (mode performa stabil sistem, V-Sync Zink, resolusi maks 80%).
    // Nilai sebelum dinyalakan disimpan supaya bisa dikembalikan saat dimatikan.
    val powerSaverMode: Boolean = false,
    val powerSaverPrevResolution: Int = 100,
    val powerSaverPrevSustained: Boolean = false,
    val powerSaverPrevVsync: Boolean = false,

    // Latar Beranda (foto / GIF / video). Bawaan mati supaya Beranda diam dan tidak memakai CPU/GPU
    // di HP kelas bawah; bisa dinyalakan di Pengaturan > Tampilan.
    val homeBgAnimate: Boolean = false, // false = GIF dan video tampil diam (paling hemat)
    val homeBgPauseOnPowerSave: Boolean = true, // jeda GIF/video saat mode Hemat Baterai HP menyala
    val homeBgSound: Boolean = false, // suara video, bawaan mati
    val homeBgVolume: Int = 50, // 0 - 100
    val homeAmbientEnabled: Boolean = false, // cahaya di sekitar kartu Beranda (hanya GIF/video)
    val homeAmbientIntensity: Int = 60, // 20 - 100

    // Kunci API CurseForge milik pengguna (diisi dari layar Mod). Kosong = pakai kunci bawaan build bila ada.
    val curseForgeApiKey: String = "",

    // Pembaruan aplikasi
    val autoCheckUpdates: Boolean = true, // cek rilis baru di GitHub setiap launcher dibuka

    // Tampilan: warna aksen ("navy", "green", "purple", "teal"), gaya ("default" atau "liquid"),
    // dan seberapa gelap latar di layar selain Home (0 - 100)
    val accentColor: String = "navy",
    val uiStyle: String = "default",
    val bgDim: Int = 50,

    // Gelombang penyederhanaan bawaan yang sudah diterapkan ke berkas pengaturan ini (0 = belum
    // pernah). Dipakai SettingsManager supaya perubahan bawaan hanya diterapkan sekali.
    val perfRev: Int = 0
)
