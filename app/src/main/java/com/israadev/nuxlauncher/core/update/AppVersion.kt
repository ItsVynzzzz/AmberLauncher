package com.israadev.nuxlauncher.core.update

/**
 * Nomor versi gaya semver, mis. 0.1.0-beta.7 (tag GitHub boleh berawalan "v"). Urutannya mengikuti
 * aturan semver, tanpa ketergantungan Android sehingga mudah diuji:
 *
 *  - 1.0.0 lebih baru daripada 1.0.0-beta.2 (rilis penuh mengalahkan versi berlabel),
 *  - 0.1.0-beta.10 lebih baru daripada 0.1.0-beta.9 (bagian angka dibandingkan sebagai angka),
 *  - 0.1.0-beta.dev (build lokal) dianggap lebih baru daripada semua 0.1.0-beta.<angka>,
 *    jadi build lokal tidak pernah ditawari "pembaruan".
 */
class AppVersion private constructor(
    private val core: List<Long>,
    private val label: List<String>
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        for (i in 0 until 3) {
            val c = core[i].compareTo(other.core[i])
            if (c != 0) return c
        }
        if (label.isEmpty() || other.label.isEmpty()) {
            return when {
                label.isEmpty() && other.label.isEmpty() -> 0
                label.isEmpty() -> 1
                else -> -1
            }
        }
        for (i in 0 until minOf(label.size, other.label.size)) {
            val a = label[i]
            val b = other.label[i]
            val an = a.toLongOrNull()
            val bn = b.toLongOrNull()
            val c = when {
                an != null && bn != null -> an.compareTo(bn)
                an != null -> -1 // bagian angka selalu lebih kecil daripada bagian teks
                bn != null -> 1
                else -> a.compareTo(b)
            }
            if (c != 0) return c
        }
        return label.size.compareTo(other.label.size)
    }

    override fun equals(other: Any?): Boolean = other is AppVersion && compareTo(other) == 0

    override fun hashCode(): Int = 31 * core.hashCode() + label.hashCode()

    override fun toString(): String =
        core.joinToString(".") + if (label.isEmpty()) "" else "-" + label.joinToString(".")

    companion object {
        private val PATTERN =
            Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$""")

        /** Mengurai "v0.1.0-beta.7" atau "0.1.0-beta.7". Null bila bukan nomor versi (mis. tag "build-12"). */
        fun parse(text: String?): AppVersion? {
            val m = PATTERN.matchEntire(text?.trim().orEmpty()) ?: return null
            val core = (1..3).map { m.groupValues[it].toLongOrNull() ?: return null }
            val label = m.groupValues[4].takeIf { it.isNotEmpty() }?.split('.') ?: emptyList()
            if (label.any { it.isEmpty() }) return null
            return AppVersion(core, label)
        }
    }
}
