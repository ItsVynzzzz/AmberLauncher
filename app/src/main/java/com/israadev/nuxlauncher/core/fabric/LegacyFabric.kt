package com.israadev.nuxlauncher.core.fabric

import com.google.gson.JsonParser
import com.israadev.nuxlauncher.core.utils.NuxVersionUtils

/**
 * Legacy Fabric: Fabric Loader untuk Minecraft lama (1.13.2 ke bawah), yang tidak dilayani Fabric resmi.
 *
 * Server metanya (meta.legacyfabric.net) adalah salinan Fabric Meta, jadi bentuk daftar versi dan profil
 * JSON-nya sama persis dengan Fabric biasa. Karena itu Legacy Fabric tidak punya jenis loader sendiri di
 * Amber: instance tetap bertipe "fabric", dan [FabricService] hanya memilih server mana yang ditanya
 * menurut versi Minecraft-nya.
 *
 * Tidak memakai Android dan jaringan sama sekali, jadi bisa diuji di JVM biasa.
 */
object LegacyFabric {

    const val META = "https://meta.legacyfabric.net/v2/versions"

    /**
     * Versi Minecraft paling lama yang ditawarkan. Versi yang lebih tua memakai susunan aset lama
     * (pre-1.6) yang belum didukung Amber, sama seperti batas Forge.
     */
    const val MIN_MC = "1.7.10"

    /** Fabric resmi mulai di Minecraft 1.14; di bawah itu dilayani Legacy Fabric. */
    private const val OFFICIAL_FIRST_MC = "1.14"

    private val releaseRegex = Regex("""^\d+\.\d+(\.\d+)?$""")

    /** True bila [mcVersion] adalah rilis 1.7.10 sampai 1.13.2, yaitu yang dilayani Legacy Fabric. */
    fun appliesTo(mcVersion: String): Boolean {
        val v = mcVersion.trim()
        return releaseRegex.matches(v) &&
            !NuxVersionUtils.isLowerVer(v, MIN_MC) &&
            NuxVersionUtils.isLowerVer(v, OFFICIAL_FIRST_MC)
    }

    /** Alamat daftar versi loader untuk [mcVersion] di server Legacy Fabric. */
    fun loadersUrl(mcVersion: String): String = "$META/loader/$mcVersion"

    /** Alamat profil JSON (kelas utama dan library) untuk [mcVersion] dan versi loader [loaderVersion]. */
    fun profileUrl(mcVersion: String, loaderVersion: String): String =
        "$META/loader/$mcVersion/$loaderVersion/profile/json"

    /** Alamat daftar versi Minecraft yang dilayani Legacy Fabric. */
    fun gamesUrl(): String = "$META/game"

    /**
     * Membaca daftar versi Minecraft dari server Legacy Fabric, hanya yang memang ditawarkan Amber
     * ([appliesTo]). Melempar bila isinya bukan daftar JSON yang benar.
     */
    fun gameVersionsFrom(json: String): Set<String> =
        JsonParser.parseString(json).asJsonArray
            .mapNotNull { element ->
                element.asJsonObject.get("version")?.asString?.trim()?.takeIf { it.isNotEmpty() }
            }
            .filter { appliesTo(it) }
            .toSet()
}
