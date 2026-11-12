package com.israadev.nuxlauncher.core.instance

import com.israadev.nuxlauncher.core.models.Instance

/**
 * Menggabungkan daftar instance dari `instances.json` dengan `instance.json` yang ditemukan di folder
 * `instances/`, dengan satu aturan yang tidak boleh dilanggar: id tidak pernah ganda.
 *
 * Daftar instance dipakai sebagai kunci item di layar pemilih instance; dua instance dengan id sama
 * membuat Compose menutup aplikasi ("Key ... was already used"). Id ganda bisa masuk lewat dua jalan:
 * `instances.json` yang sudah memuat id yang sama dua kali, atau folder instance yang disalin pengguna
 * (mis. "inst_abc (1)") yang `instance.json`-nya masih membawa id aslinya.
 *
 * - [fromIndex]: isi `instances.json`. Bila satu id muncul lagi, yang pertama dipakai.
 * - [folders]: pasangan (nama folder, instance dari instance.json-nya). Folder yang namanya sudah menjadi
 *   id instance di [fromIndex] dilewati. Selain itu folder dianggap instance sendiri, dan id-nya disamakan
 *   dengan nama folder karena data game-nya memang ada di folder itu (getInstanceDir memakai id).
 */
internal fun mergeInstances(fromIndex: List<Instance>, folders: List<Pair<String, Instance>>): List<Instance> {
    val merged = LinkedHashMap<String, Instance>()
    for (inst in fromIndex) merged.putIfAbsent(inst.id, inst)
    for ((folderName, inst) in folders) {
        if (merged.containsKey(folderName)) continue
        merged[folderName] = if (inst.id == folderName) inst else inst.copy(id = folderName)
    }
    return merged.values.toList()
}
