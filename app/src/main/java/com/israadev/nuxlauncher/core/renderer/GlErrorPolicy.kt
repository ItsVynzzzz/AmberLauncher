package com.israadev.nuxlauncher.core.renderer

/**
 * Minecraft 26.x memakai backend grafis baru (renderpearl) yang, saat membuat perangkat OpenGL,
 * sengaja memancing galat lalu memeriksa `glGetError` melaporkannya. Wrapper GL4ES/Krypton yang
 * dijalankan dengan `LIBGL_NOERROR=1` selalu menjawab "tidak ada galat", sehingga game berhenti
 * dengan "Failed to create backend OpenGL: glGetError mismatch". Untuk versi itu opsi tersebut
 * harus dimatikan (sedikit lebih lambat, tetapi game bisa jalan).
 */
object GlErrorPolicy {

    /** Versi Minecraft yang memeriksa `glGetError` saat memulai: 26.x ke atas (juga snapshot 26w..). */
    fun needsRealGlError(mcVersion: String): Boolean {
        val major = Regex("""^\s*(\d+)""").find(mcVersion)?.groupValues?.get(1)?.toIntOrNull() ?: return false
        return major >= 26
    }

    /**
     * Minecraft 26.x punya pilihan backend OpenGL/Vulkan sendiri ("preferredGraphicsBackend" di options.txt)
     * dan bisa jatuh ke Vulkan bila OpenGL gagal dibuat.
     */
    fun hasBackendChoice(mcVersion: String): Boolean = needsRealGlError(mcVersion)
}
