package com.israadev.nuxlauncher.core.forge

import com.google.gson.JsonParser

/**
 * Logika murni (tanpa jaringan) untuk versi NeoForge: pemetaan ke versi Minecraft, pengurutan, dan
 * pembacaan daftar dari server. Dipisah dari [ForgeService] supaya mudah diuji.
 *
 * Penomoran NeoForge:
 * - Gaya lama `A.B.C` berarti Minecraft `1.A.B` (B = 0 ditulis `1.A`): `20.4.237` untuk 1.20.4,
 *   `21.0.167` untuk 1.21. C adalah nomor build.
 * - Gaya baru (Minecraft bernomor tahun, `26.1` dan seterusnya) `A.B.C.D`: Minecraft `A.B` bila C = 0,
 *   selain itu `A.B.C`. D adalah nomor build. `26.1.0.5-beta` untuk Minecraft 26.1.
 * - Akhiran `-beta` berarti praliris untuk Minecraft rilis yang sama. Versi `alpha`/`snapshot`
 *   ditujukan bagi snapshot Minecraft sehingga dilewati.
 */
object NeoForgeVersions {

    /** Minecraft bernomor tahun dimulai dari 26.x; sebelumnya 1.x. */
    private const val FIRST_YEAR_BASED_MAJOR = 26

    /** Versi Minecraft yang dituju [version], atau null bila formatnya tak dikenali atau untuk snapshot. */
    fun mcVersionOf(version: String): String? {
        val v = version.trim()
        if (v.isEmpty() || v.contains('+')) return null
        val suffix = v.substringAfter('-', "").lowercase()
        if (suffix.contains("alpha") || suffix.contains("snapshot")) return null
        val numbers = numericCore(v) ?: return null
        if (numbers.size < 3) return null
        val yearBased = numbers[0] >= FIRST_YEAR_BASED_MAJOR || numbers.size >= 4
        return if (yearBased) {
            if (numbers[2] == 0) "${numbers[0]}.${numbers[1]}" else "${numbers[0]}.${numbers[1]}.${numbers[2]}"
        } else {
            if (numbers[1] == 0) "1.${numbers[0]}" else "1.${numbers[0]}.${numbers[1]}"
        }
    }

    /** Bagian angka sebelum tanda `-`, atau null bila ada bagian yang bukan angka. */
    private fun numericCore(version: String): List<Int>? {
        val parts = version.trim().substringBefore('-').split('.')
        if (parts.isEmpty()) return null
        val numbers = ArrayList<Int>(parts.size)
        for (p in parts) {
            if (p.isEmpty() || !p.all { it in '0'..'9' }) return null
            numbers.add(p.toIntOrNull() ?: return null)
        }
        return numbers
    }

    /** Praliris (beta dan sejenisnya): memuat tanda `-`. */
    fun isPrerelease(version: String): Boolean = version.contains('-')

    /** Urutan naik: angka dibandingkan satu per satu; pada angka sama, rilis stabil lebih baru dari praliris. */
    fun compare(a: String, b: String): Int {
        val na = numericCore(a) ?: emptyList()
        val nb = numericCore(b) ?: emptyList()
        for (i in 0 until maxOf(na.size, nb.size)) {
            val x = na.getOrElse(i) { 0 }
            val y = nb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        val pa = isPrerelease(a)
        val pb = isPrerelease(b)
        if (pa != pb) return if (pa) -1 else 1
        return a.compareTo(b)
    }

    /** Semua isi `<versions>` dari maven-metadata.xml. Kosong bila isinya tidak dikenali. */
    fun parseMetadataVersions(xml: String): List<String> {
        val section = xml.substringAfter("<versions>", "").substringBefore("</versions>")
        return VERSION_TAG.findAll(section).map { it.groupValues[1] }.toList()
    }

    /** Daftar dari BMCLAPI: larik objek (`version`/`rawVersion`) atau larik teks. Isi yang aneh dilewati. */
    fun parseBmclList(json: String): List<String> {
        val root = JsonParser.parseString(json)
        if (!root.isJsonArray) return emptyList()
        val result = ArrayList<String>()
        for (item in root.asJsonArray) {
            when {
                item.isJsonPrimitive && item.asJsonPrimitive.isString -> result.add(item.asString)
                item.isJsonObject -> {
                    val obj = item.asJsonObject
                    val v = listOf("version", "rawVersion")
                        .mapNotNull { key ->
                            obj.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                        }
                        .map { it.trim() }
                        .firstOrNull { it.isNotEmpty() }
                    if (v != null) result.add(v)
                }
            }
        }
        return result.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** Versi NeoForge dikelompokkan per versi Minecraft; tiap daftar terbaru lebih dulu. */
    fun groupByMc(versions: List<String>): Map<String, List<String>> =
        versions.distinct()
            .mapNotNull { v -> mcVersionOf(v)?.let { it to v } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, list) -> list.sortedWith { a, b -> compare(b, a) } }

    /**
     * Build untuk [mcVersion]: hanya versi yang memang menuju Minecraft itu, terbaru lebih dulu.
     * "recommended" = rilis stabil terbaru (bila ada); "latest" = yang terbaru dari semuanya.
     */
    fun toBuilds(mcVersion: String, versions: List<String>): List<ForgeBuild> {
        val sorted = versions.distinct()
            .filter { mcVersionOf(it) == mcVersion }
            .sortedWith { a, b -> compare(b, a) }
        val latest = sorted.firstOrNull()
        val recommended = sorted.firstOrNull { !isPrerelease(it) }
        return sorted.map { ForgeBuild(mcVersion, it, recommended = it == recommended, latest = it == latest) }
    }

    private val VERSION_TAG = Regex("<version>\\s*([^<\\s]+)\\s*</version>")
}
