package com.israadev.nuxlauncher.core.forge

/**
 * Model data Forge (hanya Forge modern, Minecraft 1.13 ke atas).
 *
 * Semua field dibuat nullable dengan nilai bawaan karena diisi Gson dari JSON milik installer Forge
 * (Gson tidak memanggil konstruktor, jadi field yang tidak ada di JSON bernilai null).
 */

/** Satu build Forge untuk satu versi Minecraft. [version] contoh "47.2.0". */
data class ForgeBuild(
    val mcVersion: String,
    val version: String,
    val recommended: Boolean = false,
    val latest: Boolean = false
) {
    /** Versi artefak Maven: "<mc>-<forge>", contoh "1.20.1-47.2.0". */
    val mavenVersion: String get() = "$mcVersion-$version"
}

/** Isi `install_profile.json` di dalam forge-installer.jar. */
data class ForgeInstallProfile(
    val spec: Int? = null,
    /** Id versi hasil pemasangan, contoh "1.20.1-forge-47.2.0". */
    val version: String? = null,
    val minecraft: String? = null,
    /** Lokasi version.json di dalam installer, contoh "/version.json". */
    val json: String? = null,
    val data: Map<String, ForgeDataEntry>? = null,
    val processors: List<ForgeProcessor>? = null,
    val libraries: List<ForgeLibrary>? = null
)

data class ForgeDataEntry(
    val client: String? = null,
    val server: String? = null
)

data class ForgeProcessor(
    val sides: List<String>? = null,
    val jar: String? = null,
    val classpath: List<String>? = null,
    val args: List<String>? = null,
    val outputs: Map<String, String>? = null
)

data class ForgeLibrary(
    val name: String? = null,
    val downloads: ForgeLibDownloads? = null,
    /** Alamat dasar Maven untuk library bergaya lama yang tidak punya `downloads`. */
    val url: String? = null
)

data class ForgeLibDownloads(
    val artifact: ForgeArtifact? = null
)

data class ForgeArtifact(
    val path: String? = null,
    val url: String? = null,
    val sha1: String? = null,
    val size: Long? = null
)

/** Isi `version.json` Forge (mewarisi versi Minecraft lewat [inheritsFrom]). */
data class ForgeVersionJson(
    val id: String? = null,
    val inheritsFrom: String? = null,
    val mainClass: String? = null,
    val minecraftArguments: String? = null,
    val arguments: ForgeArguments? = null,
    val libraries: List<ForgeLibrary>? = null
)

/** Elemen berupa String, atau objek berisi `rules` dan `value` (diurai Gson sebagai Map). */
data class ForgeArguments(
    val game: List<Any>? = null,
    val jvm: List<Any>? = null
)

/** Cara menghitung path dan alamat library dari koordinat Maven. */
object ForgeMaven {

    /**
     * "group:artifact:version[:classifier][@ext]" menjadi
     * "group/artifact/version/artifact-version[-classifier].ext". Null bila koordinat tidak valid.
     */
    fun pathOf(coordinate: String?): String? {
        var c = coordinate?.trim() ?: return null
        var ext = "jar"
        val at = c.indexOf('@')
        if (at >= 0) {
            ext = c.substring(at + 1)
            c = c.substring(0, at)
        }
        val p = c.split(':')
        if (p.size < 3 || p[0].isBlank() || p[1].isBlank() || p[2].isBlank()) return null
        val group = p[0].replace('.', '/')
        val artifact = p[1]
        val version = p[2]
        val classifier = if (p.size > 3 && p[3].isNotEmpty()) "-${p[3]}" else ""
        return "$group/$artifact/$version/$artifact-$version$classifier.$ext"
    }

    /** "group:artifact" (tanpa versi dan classifier), dipakai untuk mendeteksi library yang ditimpa. */
    fun keyOf(coordinate: String?): String? {
        val p = coordinate?.substringBefore('@')?.split(':') ?: return null
        return if (p.size >= 2) "${p[0]}:${p[1]}" else null
    }

    /** Isi `data` install_profile bertanda kurung siku "[koordinat]" menjadi path relatif. */
    fun pathOfDataEntry(value: String?): String? {
        val v = value?.trim() ?: return null
        if (!v.startsWith("[") || !v.endsWith("]")) return null
        return pathOf(v.substring(1, v.length - 1))
    }

    /** Path relatif library di folder libraries (dari `downloads.artifact.path`, atau dari namanya). */
    fun libraryPath(lib: ForgeLibrary): String? {
        return lib.downloads?.artifact?.path?.takeIf { it.isNotBlank() } ?: pathOf(lib.name)
    }

    /** Alamat unduhan library; kosong bila library itu dibawa di dalam installer. */
    fun libraryUrl(lib: ForgeLibrary): String? {
        lib.downloads?.artifact?.let { artifact ->
            return artifact.url?.takeIf { it.isNotBlank() }
        }
        // Library gaya lama: hanya nama (+ alamat dasar Maven bila ada)
        val path = pathOf(lib.name) ?: return null
        val base = lib.url?.takeIf { it.isNotBlank() } ?: "https://libraries.minecraft.net/"
        return base.trimEnd('/') + "/" + path
    }
}
