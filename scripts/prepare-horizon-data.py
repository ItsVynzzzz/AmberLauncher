#!/usr/bin/env python3
"""Menyiapkan tabel data mesin Rust (Horizon) memakai berkas versi Mojang yang DIPATOK proyek sumber.

Dipanggil oleh scripts/build-horizon-engine.sh sebagai pengganti langsung `python3 tools/android_prepare.py`:

    prepare-horizon-data.py FOLDER_SUMBER [argumen android_prepare.py ...]

Kenapa ada: tools/android_prepare.py mengunduh berkas versi (26.3.json) menurut daftar Mojang SAAT INI. Mojang
sesekali menerbitkan ulang berkas itu (biasanya hanya indeks aset terjemahan yang berubah), dan horizon-prepare
menolak berkas yang SHA-1 nya tidak dikenal proyek: "is not a 26.3 version file Horizon knows". Proyek sumber
sendiri menyarankan memakai salinan Mojang yang dipatok, di
https://piston-meta.mojang.com/v1/packages/<manifest_sha1>/<versi>.json. Skrip ini melakukan itu: di daftar
Mojang, entri versi yang dipatok diarahkan ke salinan tersebut, lalu seluruh alur android_prepare.py berjalan
seperti biasa. Unduhan tetap diperiksa SHA-1 nya (lab.download), jadi salinan yang salah tidak akan diterima.
"""
import json
import sys
from pathlib import Path


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    source = Path(sys.argv[1]).resolve()
    rest = sys.argv[2:]
    sys.path.insert(0, str(source / 'tools'))
    import android_prepare as prepare

    lock = json.loads((source / 'research-lock.json').read_text(encoding='utf-8'))['minecraft']
    version, pinned = lock['version'], lock['manifest_sha1']
    pinned_url = f'https://piston-meta.mojang.com/v1/packages/{pinned}/{version}.json'
    fetch_json = prepare.fetch_json

    def pinned_listing(url):
        data = fetch_json(url)
        if url == prepare.MANIFEST:
            for entry in data['versions']:
                if entry['id'] == version:
                    if entry['sha1'] != pinned:
                        print(f"Mojang kini mencantumkan {version} dengan sha1 {entry['sha1']}; "
                              f"dipakai salinan yang dipatok {pinned}", flush=True)
                    entry['url'] = pinned_url
                    entry['sha1'] = pinned
        return data

    prepare.fetch_json = pinned_listing
    sys.argv = [str(source / 'tools/android_prepare.py'), *rest]
    prepare.main()


if __name__ == '__main__':
    main()
