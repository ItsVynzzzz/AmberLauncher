#!/usr/bin/env python3
"""Membuat catatan rilis dari changelog/entries/*.txt.

Tiap patch membawa satu berkas catatan di changelog/entries/ (format di changelog/README.md). Skrip ini
dipanggil workflow saat build dan membuat dua hal dari catatan yang ditambahkan sejak rilis sebelumnya:

  --asset     JSON untuk dibundel ke APK (app/src/main/assets/whatsnew.json), dibaca layar "Yang baru"
  --markdown  isi catatan untuk halaman Release GitHub (butir bahasa Inggris, plus blok tersembunyi
              berisi semua bahasa yang dibaca aplikasi untuk pop-up pembaruan)

Hanya pustaka standar Python. Bila tidak ada catatan, tidak ada berkas yang ditulis dan kode keluarnya 3,
supaya workflow bisa memakai cara lama (judul commit).
"""
import argparse
import json
import os
import re
import subprocess
import sys

LANGS = ["id", "en", "es", "fil", "pt", "th", "vi", "zh"]
KINDS = {"+": "new", "~": "improve", "!": "fix"}
KIND_TITLE_EN = {"new": "New", "improve": "Improved", "fix": "Fixed"}
SECTION = re.compile(r"^\[([a-z]{2,3})\]\s*$")
EMPTY = 3


def parse_entry(text):
    """Satu berkas -> {bahasa: [(jenis, teks), ...]}. Baris kosong dan berawalan # diabaikan."""
    out = {}
    lang = None
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        m = SECTION.match(line)
        if m:
            lang = m.group(1)
            out.setdefault(lang, [])
            continue
        if lang is None or line[0] not in KINDS:
            continue
        body = line[1:].strip()
        if body:
            out[lang].append((KINDS[line[0]], body))
    return out


def entry_files(directory, since):
    """Berkas catatan yang belum ada pada tag [since] (semuanya bila since kosong atau tag tidak ditemukan)."""
    names = sorted(n for n in os.listdir(directory) if n.endswith(".txt")) if os.path.isdir(directory) else []
    if not since:
        return names
    r = subprocess.run(["git", "ls-tree", "-r", "--name-only", since, "--", directory],
                       capture_output=True, text=True)
    if r.returncode != 0:
        return names
    old = {os.path.basename(p) for p in r.stdout.splitlines()}
    return [n for n in names if n not in old]


def collect(directory, since):
    """{bahasa: [(jenis, teks)]}. Bahasa yang belum diterjemahkan pada suatu catatan memakai Inggris, lalu Indonesia."""
    merged = {lang: [] for lang in LANGS}
    for name in entry_files(directory, since):
        with open(os.path.join(directory, name), encoding="utf-8") as f:
            entry = parse_entry(f.read())
        for lang in LANGS:
            items = entry.get(lang) or entry.get("en") or entry.get("id") or []
            merged[lang].extend(items)
    return merged


def safe_json(obj):
    """JSON satu baris, ASCII saja, aman di dalam komentar HTML (tanpa '--', '<', '>')."""
    s = json.dumps(obj, ensure_ascii=True, separators=(",", ":"))
    return s.replace("-", "\\u002d").replace("<", "\\u003c").replace(">", "\\u003e")


def payload(version, merged):
    return {"v": 1, "version": version,
            "lang": {lang: [[k, t] for k, t in items] for lang, items in merged.items()}}


def markdown(version, merged, repo_url):
    lines = []
    for kind in ("new", "improve", "fix"):
        items = [t for k, t in merged["en"] if k == kind]
        if items:
            lines += ["### " + KIND_TITLE_EN[kind], ""] + ["- " + t for t in items] + [""]
    # Awalan "https://" membuat aplikasi lama menyaring baris ini dari pop-up pembaruan mereka.
    lines.append("<!-- amber-whatsnew:v1 %s %s -->" % (repo_url, safe_json(payload(version, merged))))
    return "\n".join(lines) + "\n"


def write_atomic(path, text):
    directory = os.path.dirname(path)
    if directory:
        os.makedirs(directory, exist_ok=True)
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    os.replace(tmp, path)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--version", required=True)
    ap.add_argument("--since", default="", help="tag rilis sebelumnya; kosong = semua catatan")
    ap.add_argument("--entries", default="changelog/entries")
    ap.add_argument("--asset", help="tulis JSON untuk APK ke berkas ini")
    ap.add_argument("--markdown", help="tulis isi catatan Release GitHub ke berkas ini")
    ap.add_argument("--repo-url", default="https://github.com")
    args = ap.parse_args(argv)

    merged = collect(args.entries, args.since)
    if not any(merged.values()):
        print("Tidak ada catatan baru di %s sejak %s." % (args.entries, args.since or "awal"), file=sys.stderr)
        return EMPTY
    data = payload(args.version, merged)
    if args.asset:
        write_atomic(args.asset, json.dumps(data, ensure_ascii=False, separators=(",", ":")) + "\n")
    if args.markdown:
        write_atomic(args.markdown, markdown(args.version, merged, args.repo_url))
    print("Catatan: %d butir (id), sejak %s." % (len(merged["id"]), args.since or "awal"), file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
