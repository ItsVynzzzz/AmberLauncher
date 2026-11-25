#!/usr/bin/env python3
"""Catatan changelog untuk Discord: isi pesan webhook (embed) dan pesan commit.

Sumbernya sama dengan catatan rilis: changelog/entries/*.txt (format di changelog/README.md).
Pilih satu sumber catatan:

  --staged        catatan yang baru ditambahkan di index git (patch yang baru diterapkan, belum di-commit)
  --added-in REV  catatan yang ditambahkan oleh commit REV (mis. HEAD)
  --since TAG     semua catatan yang belum ada di tag TAG (kosong = semuanya), seperti catatan rilis

Pilih satu keluaran:

  --out BERKAS       tulis JSON untuk webhook Discord (kirim dengan curl)
  --commit-message   cetak pesan commit: judul satu baris yang informatif + daftar butir

Hanya pustaka standar Python. Kode keluar 3 bila tidak ada catatan, supaya workflow bisa melewati
langkahnya tanpa dianggap gagal.
"""
import argparse
import datetime
import importlib.util
import json
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
_spec = importlib.util.spec_from_file_location("release_notes", os.path.join(HERE, "release-notes.py"))
rn = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(rn)

EMPTY = rn.EMPTY
JUDUL = re.compile(r"^#\s*judul\s*:\s*(.+?)\s*$", re.I | re.M)
KIND_TITLE = {
    "id": {"new": "Baru", "improve": "Peningkatan", "fix": "Perbaikan"},
    "en": {"new": "New", "improve": "Improved", "fix": "Fixed"},
}
MORE = {"id": "dan %d butir lain, selengkapnya di halaman Release.",
        "en": "and %d more, see the release page."}
DESC_LIMIT = 3600       # batas Discord untuk deskripsi embed 4096; sisakan ruang
COLOR = 0xFFBF00        # kuning amber


def git(*args):
    r = subprocess.run(["git", *args], capture_output=True, text=True)
    return r.stdout.splitlines() if r.returncode == 0 else []


def files_for(args):
    d = args.entries
    if args.staged:
        paths = git("diff", "--cached", "--name-only", "--diff-filter=A", "--", d)
    elif args.added_in:
        paths = git("diff-tree", "--no-commit-id", "--name-only", "-r", "--diff-filter=A", args.added_in, "--", d)
    else:
        return [os.path.join(d, n) for n in rn.entry_files(d, args.since)]
    return sorted(p for p in paths if p.endswith(".txt") and os.path.isfile(p))


def load(paths, lang):
    """-> (butir [(jenis, teks)], judul pertama atau None). Bahasa kosong memakai Inggris, lalu Indonesia."""
    items, title = [], None
    for p in paths:
        with open(p, encoding="utf-8") as f:
            text = f.read()
        if title is None:
            m = JUDUL.search(text)
            if m:
                title = m.group(1)
        entry = rn.parse_entry(text)
        items += entry.get(lang) or entry.get("en") or entry.get("id") or []
    return items, title


def short(text, limit):
    text = " ".join(text.split())
    first = re.split(r"(?<=[.!?])\s", text, maxsplit=1)[0]
    if len(first) <= limit:
        return first.rstrip(".")
    cut = first[:limit].rsplit(" ", 1)[0].rstrip(",;:- ")
    return cut + "…"


def commit_message(items, title):
    head = title or short(items[0][1], 56)
    return "Terapkan patch: %s (build lolos)\n\n%s\n" % (head, "\n".join("- " + t for _, t in items))


def embed(items, lang, version, jenis, url, commit):
    titles = KIND_TITLE.get(lang, KIND_TITLE["en"])
    blocks, used, shown = [], 0, 0
    total = len(items)
    for kind in ("new", "improve", "fix"):
        lines = []
        for k, t in items:
            if k != kind:
                continue
            line = "• " + t
            if used + len(line) + len(titles[kind]) + 8 > DESC_LIMIT:
                break
            lines.append(line)
            used += len(line) + 1
            shown += 1
        if lines:
            blocks.append("**%s**\n%s" % (titles[kind], "\n".join(lines)))
            used += len(titles[kind]) + 6
    desc = "\n\n".join(blocks)
    if shown < total:
        desc += "\n\n" + (MORE.get(lang) or MORE["en"]) % (total - shown)
    if jenis == "rilis":
        name, foot = "AmberLauncher %s" % version, "Rilis"
    else:
        name, foot = "Build uji %s" % version, "Build uji, bukan rilis"
    if commit:
        foot += " · commit " + commit
    card = {
        "title": name[:256],
        "description": desc,
        "color": COLOR,
        "footer": {"text": foot[:2048]},
        "timestamp": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"),
    }
    if url:
        card["url"] = url
    return {"username": "AmberLauncher", "allowed_mentions": {"parse": []}, "embeds": [card]}


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    src = ap.add_mutually_exclusive_group(required=True)
    src.add_argument("--staged", action="store_true")
    src.add_argument("--added-in", metavar="REV")
    src.add_argument("--since", metavar="TAG", help="kosong = semua catatan")
    ap.add_argument("--entries", default="changelog/entries")
    ap.add_argument("--lang", default="id")
    ap.add_argument("--version", default="")
    ap.add_argument("--jenis", choices=["rilis", "uji"], default="uji")
    ap.add_argument("--url", default="")
    ap.add_argument("--commit", default="")
    out = ap.add_mutually_exclusive_group(required=True)
    out.add_argument("--out", metavar="BERKAS")
    out.add_argument("--commit-message", action="store_true")
    a = ap.parse_args(argv)

    items, title = load(files_for(a), a.lang)
    if not items:
        print("Tidak ada catatan baru.", file=sys.stderr)
        return EMPTY
    if a.commit_message:
        sys.stdout.write(commit_message(items, title))
    else:
        data = embed(items, a.lang, a.version, a.jenis, a.url, a.commit)
        rn.write_atomic(a.out, json.dumps(data, ensure_ascii=False) + "\n")
        print("Pesan Discord: %d butir." % len(items), file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
