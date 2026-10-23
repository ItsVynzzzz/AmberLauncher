package com.israadev.nuxlauncher.core.mods

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.util.zip.ZipFile

/** Info singkat sebuah mod yang dibaca dari jar-nya. Setiap bagian boleh kosong. */
internal class ModJarInfo(val name: String?, val version: String?, val description: String?)

/**
 * Membaca nama, versi, dan deskripsi mod dari jar-nya untuk daftar "Mod terpasang".
 * Mendukung Fabric (`fabric.mod.json`), Forge (`META-INF/mods.toml`), dan NeoForge
 * (`META-INF/neoforge.mods.toml`, formatnya sama dengan mods.toml Forge).
 */
internal object ModMetadata {

    private const val JAR_VERSION_PLACEHOLDER = "\${file.jarVersion}"

    /** Info mod dari [zip], atau null bila jar tidak memuat berkas deskripsi yang dikenal. */
    fun read(zip: ZipFile): ModJarInfo? {
        zip.getEntry("fabric.mod.json")?.let { entry ->
            return zip.getInputStream(entry).bufferedReader().use { reader ->
                val json = JsonParser.parseReader(reader).asJsonObject
                ModJarInfo(
                    name = text(json.get("name")),
                    version = text(json.get("version")),
                    description = text(json.get("description"))
                )
            }
        }
        // NeoForge lebih dulu: jar yang dibuat untuk keduanya memuat dua berkas toml
        for (path in listOf("META-INF/neoforge.mods.toml", "META-INF/mods.toml")) {
            val entry = zip.getEntry(path) ?: continue
            val toml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            return parseModsToml(toml) { readManifestVersion(zip) }
        }
        return null
    }

    private fun text(element: JsonElement?): String? =
        element?.takeIf { it.isJsonPrimitive }?.asString

    /** `Implementation-Version` dari MANIFEST.MF; dipakai bila mods.toml menulis `${file.jarVersion}`. */
    private fun readManifestVersion(zip: ZipFile): String? {
        val entry = zip.getEntry("META-INF/MANIFEST.MF") ?: return null
        return zip.getInputStream(entry).bufferedReader().use { reader ->
            reader.readLines()
                .firstOrNull { it.startsWith("Implementation-Version:", ignoreCase = true) }
                ?.substringAfter(':')
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }
    }

    /**
     * Membaca blok `[[mods]]` pertama dari mods.toml. Nilai yang didukung: teks "..." atau '...' satu
     * baris, dan teks banyak baris """...""" atau '''...'''. Versi `${file.jarVersion}` diganti hasil
     * [jarVersion]; placeholder lain yang tak terselesaikan dibuang (null) supaya tidak tampil mentah.
     */
    fun parseModsToml(text: String, jarVersion: () -> String?): ModJarInfo? {
        val lines = text.lines()
        var i = 0
        while (i < lines.size && !lines[i].trim().startsWith("[[mods]]")) i++
        if (i >= lines.size) return null
        i++

        var modId: String? = null
        var displayName: String? = null
        var version: String? = null
        var description: String? = null

        while (i < lines.size) {
            val line = lines[i].trim()
            i++
            if (line.isEmpty() || line.startsWith("#")) continue
            if (line.startsWith("[")) break // tabel berikutnya ([[dependencies.x]] dan sebagainya)

            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val key = line.substring(0, eq).trim()
            var rest = line.substring(eq + 1).trim()

            val value: String?
            val delimiter = when {
                rest.startsWith("\"\"\"") -> "\"\"\""
                rest.startsWith("'''") -> "'''"
                else -> null
            }
            if (delimiter != null) {
                rest = rest.substring(3)
                val sb = StringBuilder()
                var closed = rest.indexOf(delimiter)
                if (closed >= 0) {
                    sb.append(rest, 0, closed)
                } else {
                    sb.append(rest)
                    while (i < lines.size) {
                        val next = lines[i]
                        i++
                        closed = next.indexOf(delimiter)
                        if (closed >= 0) {
                            sb.append('\n').append(next, 0, closed)
                            break
                        }
                        sb.append('\n').append(next)
                    }
                }
                value = sb.toString().trim().replace(Regex("\\s*\\n\\s*"), " ")
            } else {
                value = singleLineString(rest)
            }

            when (key) {
                "modId" -> modId = value
                "displayName" -> displayName = value
                "version" -> version = value
                "description" -> description = value
            }
        }

        val rawVersion = version
        val resolvedVersion = when {
            rawVersion == null -> null
            rawVersion.contains(JAR_VERSION_PLACEHOLDER) -> jarVersion()
            rawVersion.contains("\${") -> null
            else -> rawVersion
        }
        val name = displayName?.takeIf { it.isNotBlank() } ?: modId
        if (name == null && resolvedVersion == null && description == null) return null
        return ModJarInfo(name, resolvedVersion, description)
    }

    /** Nilai TOML satu baris: "teks" (dengan \" dan \\), 'teks', atau tanpa tanda kutip; komentar `#` dibuang. */
    private fun singleLineString(raw: String): String? {
        if (raw.isEmpty()) return null
        val quote = raw[0]
        if (quote == '"') {
            val sb = StringBuilder()
            var i = 1
            while (i < raw.length) {
                val c = raw[i]
                if (c == '\\' && i + 1 < raw.length) {
                    when (val n = raw[i + 1]) {
                        'n' -> sb.append(' ')
                        't' -> sb.append(' ')
                        else -> sb.append(n)
                    }
                    i += 2
                    continue
                }
                if (c == '"') return sb.toString()
                sb.append(c)
                i++
            }
            return sb.toString()
        }
        if (quote == '\'') {
            val end = raw.indexOf('\'', 1)
            return if (end > 0) raw.substring(1, end) else raw.substring(1)
        }
        return raw.substringBefore('#').trim().takeIf { it.isNotEmpty() }
    }
}
