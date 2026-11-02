package com.israadev.nuxlauncher.core.plugin

/**
 * Penyaring argumen JVM dari metadata `environment` plugin native library. Logika murni (tanpa Android)
 * supaya bisa diuji langsung di JVM.
 */
object NativePluginArgs {
    const val PLACEHOLDER = "{nativeLibraryDir}"

    /**
     * Kunci properti sistem yang dikelola launcher. Plugin tidak boleh menimpanya: nilai yang salah
     * membuat JVM atau LWJGL gagal start. Properti lain (mis. `org.sqlite.*`) bebas.
     */
    private val PROTECTED_KEYS = setOf(
        "java.home", "java.library.path", "java.class.path", "java.io.tmpdir",
        "user.home", "os.name", "os.version", "jna.boot.library.path"
    )

    private val WHITESPACE = Regex("\\s+")

    /**
     * Mengubah string `environment` (dipisah spasi) menjadi argumen JVM yang aman:
     *  - hanya properti sistem `-Dkunci` atau `-Dkunci=nilai`; argumen lain (-javaagent, -XX, --add-opens, dst.)
     *    bisa menyuntikkan kode atau mengubah JVM dan tidak dibutuhkan untuk menunjuk pustaka;
     *  - kunci terlindungi dan `org.lwjgl.*` dibuang;
     *  - `{nativeLibraryDir}` diganti [libDir]; nilai dengan segmen ".." ditolak (anti keluar folder).
     * Setiap token yang dibuang dilaporkan lewat [onReject] (alasannya untuk log).
     */
    fun sanitize(environment: String, libDir: String, onReject: (String) -> Unit = {}): List<String> {
        if (environment.isBlank()) return emptyList()
        val out = ArrayList<String>()
        for (token in environment.trim().split(WHITESPACE)) {
            if (token.isEmpty()) continue
            if (!token.startsWith("-D")) {
                onReject("hanya -D yang diizinkan: $token")
                continue
            }
            val eq = token.indexOf('=')
            val key = if (eq < 0) token.substring(2) else token.substring(2, eq)
            if (key.isEmpty() || key in PROTECTED_KEYS || key.startsWith("org.lwjgl.")) {
                onReject("properti dikelola launcher: $token")
                continue
            }
            if (eq < 0) {
                out.add(token)
                continue
            }
            val raw = token.substring(eq + 1)
            val value = if (raw.contains(PLACEHOLDER)) raw.replace(PLACEHOLDER, libDir) else raw
            if (value.split('/', ',', ':').any { it == ".." }) {
                onReject("path mengandung '..': $token")
                continue
            }
            out.add("-D$key=$value")
        }
        return out
    }
}
