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
 *   id atau nama folder instance di [fromIndex] dilewati. Selain itu folder dianggap instance yang hilang
 *   dari daftar: id aslinya dipertahankan bila masih bebas (nama foldernya dicatat di dirName), kalau tidak
 *   id-nya disamakan dengan nama folder, karena data game-nya memang ada di folder itu.
 */
internal fun mergeInstances(fromIndex: List<Instance>, folders: List<Pair<String, Instance>>): List<Instance> {
    val merged = LinkedHashMap<String, Instance>()
    for (inst in fromIndex) merged.putIfAbsent(inst.id, inst)
    val usedFolders = HashSet<String>()
    for (inst in merged.values) {
        usedFolders.add(inst.id)
        inst.dirName?.let { usedFolders.add(it) }
    }
    val folderNames = folders.mapTo(HashSet()) { it.first }
    for ((folderName, inst) in folders) {
        if (folderName in usedFolders || merged.containsKey(folderName)) continue
        val keepId = inst.id == folderName ||
            (inst.id.isNotBlank() && !merged.containsKey(inst.id) && inst.id !in folderNames && inst.id !in usedFolders)
        val recovered = when {
            // Folder gaya lama: namanya sama dengan id
            inst.id == folderName -> inst.copy(dirName = null)
            // Folder bernama dari nama instance: id aslinya masih bebas
            keepId && InstanceFolders.isSafe(folderName) -> inst.copy(dirName = folderName)
            // Salinan folder dengan id yang sudah dipakai: jadi instance sendiri berid nama folder
            else -> inst.copy(id = folderName, dirName = null)
        }
        merged[recovered.id] = recovered
        usedFolders.add(folderName)
        usedFolders.add(recovered.id)
    }
    return merged.values.toList()
}
