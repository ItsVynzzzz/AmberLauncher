package com.israadev.nuxlauncher.core.models

import com.israadev.nuxlauncher.core.streamer.StreamerItem

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
    // Geser antar tombol: menggeser jari dari satu tombol keyboard ke tombol lain (mis. W ke D) langsung menekan
    // tombol yang dilewati tanpa mengangkat jari. Bawaan nyala. Nilai ini juga dipakai pengguna lama: tanpa kolom
    // ini di berkas pengaturan tersimpan, bawaan di sini yang berlaku.
    val buttonSlide: Boolean = true,
    // Discord Rich Presence ("Bermain AmberLauncher" di profil Discord). Bawaan MATI: pemain memilih sendiri.
    // Hanya berarti pada build yang membawa SDK Discord (BuildConfig.HAS_DISCORD_SDK).
    val discordRpcEnabled: Boolean = false,
    // Presence juga saat launcher terbuka dan belum bermain ("Getting ready to play"). Bawaan MATI, terpisah dari yang di atas.
    val discordRpcLauncherEnabled: Boolean = false,
    // Alamat server multiplayer yang sedang dimainkan ikut tampil di Discord (loader Java). Bawaan MATI.
    val discordRpcShowServer: Boolean = false,
    // Nama instance ikut tampil di Discord (bisa berisi nama pribadi, jadi bawaan MATI).
    val discordRpcShowInstance: Boolean = false,
    // Bila alamat server tampil: ikon server jadi gambar besar di profil Discord. Bawaan NYALA (tak berpengaruh bila server tak tampil).
    val discordRpcServerIcon: Boolean = true,
    // Eksperimental, bawaan mati. Bila modnya terpasang dan tersambung, sentuhan juga diteruskan ke mod
    // sementara tombol dan mouse virtual launcher tetap aktif. Namanya sengaja baru supaya nilai "aktif"
    // dari versi sebelumnya tidak terbawa.
    val touchControllerEnabled: Boolean = false,
    val mouseSizeDp: Int = 24, // 16 - 48 dp
    // Mesin Rust: tombol layar mengikuti tata letak kontrol Amber (bukan kontrol bawaan mesin). Bawaan mati. Diterapkan saat PLAY.
    val rustAmberControls: Boolean = false,
    // Mesin Rust: tombol bergaris tiga di layar game yang membuka menu cepat mesin. Bawaan nyala. Diterapkan saat PLAY.
    val rustControlHub: Boolean = true,
    val hideMouseInClickMode: Boolean = true,

    // Mouse fisik. false (bawaan) = mouse fisik menggerakkan kursor launcher di menu (pointer capture, jalur yang
    // sama dengan saat bermain), jadi gambar, ukuran, dan hotspot kursor bisa diatur. true = kursor sistem Android.
    val physicalMouseSystemPointer: Boolean = false,
    // Pengali (persen) di atas sensitivitas kursor / kamera di atas, khusus mouse fisik. 100 = sama persis.
    val physicalMouseCursorSpeed: Int = 100, // 20 - 300
    val physicalMouseCameraSpeed: Int = 100, // 20 - 300
    // Hotspot kursor per bentuk, dalam persen kotak kursor: "arrow.x", "arrow.y", "hand.x", dst. Kosong = bawaan.
    val pointerHotspots: Map<String, Int> = emptyMap(),

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
    // Permukaan game selalu TextureView (bukan SurfaceView). Perbaikan bila tampilan game terputar 90 derajat
    // di HP yang layar alaminya portrait; TextureView sedikit lebih berat (bawaan mati).
    val forceTextureView: Boolean = false,
    // Tampilan angka FPS dan RAM di layar game. Dimatikan di sini atau lewat menu dalam game (ketuk angkanya).
    val showFpsHud: Boolean = true,
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

    // Perbarui mod otomatis saat Manajemen Mod (tab Mod) dibuka, paling sering sekali per 6 jam per instance.
    // Bawaan mati: mod yang berubah sendiri bisa memutus dunia atau modpack yang sedang dimainkan.
    val autoUpdateMods: Boolean = false,

    // Pembaruan aplikasi
    val autoCheckUpdates: Boolean = true, // cek rilis baru di GitHub setiap launcher dibuka

    // Tampilan: warna aksen ("navy", "green", "purple", "teal"), gaya ("default", "liquid", atau "simple"),
    // dan seberapa gelap latar di layar selain Home (0 - 100)
    val accentColor: String = "navy",
    val uiStyle: String = "default",
    val bgDim: Int = 50,
    // Warna gaya Simple (hanya dipakai saat uiStyle = "simple"): rona 0 - 359 dan kepekatan warna 0 - 100
    // (0 = abu-abu netral). Gaya lain memakai accentColor.
    val simpleHue: Int = 215,
    val simpleTone: Int = 55,

    // Efek salju (suasana Natal): bawaan mati. Di Beranda lebat; di layar lain lebih sedikit dan redup
    // seperti di dalam ruangan. Bisa dinyalakan di Pengaturan > Tampilan.
    val snowEnabled: Boolean = false,
    val snowAmount: Int = 60, // 10 - 100 (jumlah untuk semua efek beranda, bukan hanya salju)
    // Jenis efek beranda: snow, sakura, leaves, rain, fireflies, sparkle, embers, bubbles. snowEnabled = saklar utamanya.
    val homeEffect: String = "snow",

    // Pratinjau skin akun aktif di Home (figur diam di kanan atas tombol PLAY, geser untuk memutar).
    // Digambar di kanvas tanpa animasi, tapi bisa dimatikan untuk HP yang sangat lemah.
    val homeSkinPreview: Boolean = true,

    // Penyesuaian ukuran, dua pengaturan terpisah. 100 = ukuran bawaan (tidak mengubah apa pun).
    // uiScalePercent: seluruh UI launcher (70 - 130). homeSkinScalePercent: hanya figur skin di Home (50 - 130).
    val uiScalePercent: Int = 100,
    val homeSkinScalePercent: Int = 100,

    // Mode streamer: overlay (teks, gambar, link dengan kode QR, timer, overlay web) di atas game, dinyalakan dan
    // dimatikan lewat panel kiri di menu dalam game. Bawaan mati. Daftar overlay diatur di Pengaturan > Game > Mode streamer.
    val streamerMode: Boolean = false,
    val streamerItems: List<StreamerItem> = emptyList(),

    // Gelombang penyederhanaan bawaan yang sudah diterapkan ke berkas pengaturan ini (0 = belum
    // pernah). Dipakai SettingsManager supaya perubahan bawaan hanya diterapkan sekali.
    val perfRev: Int = 0
)
