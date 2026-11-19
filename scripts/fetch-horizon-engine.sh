#!/usr/bin/env bash
# Memasang mesin Rust (Horizon) ke pohon kerja dari Release repo ini sendiri, lalu menaruh dua berkas yang
# dikemas Gradle:
#   app/src/main/jniLibs/<abi>/libhorizon_android.so        mesinnya (abi: arm64-v8a, atau armeabi-v7a dengan --abi)
#   app/src/main/assets/engine-data.zip                     tabel data mesin
# beserta pemberitahuan lisensi MIT-nya di app/src/main/assets/licenses/horizon/.
# Semuanya ada di .gitignore dan tidak pernah disimpan ke repo.
#
# Mesinnya dikompilasi dari sumber oleh workflow "Bangun mesin Rust (Horizon)" (scripts/build-horizon-engine.sh)
# dan diterbitkan sebagai Release bertanda horizon-engine-<12 digit commit>[-<patch_set>-<hash isi patch>][-fast].
# Hash isi patch ikut di tanda itu, jadi mengedit patch Amber otomatis berarti mesin baru. Skrip ini hanya
# mengambil hasilnya.
# Dipanggil otomatis oleh Gradle sebelum build (app/build.gradle.kts, tugas fetchHorizonEngine).
#
# OPSIONAL dan tidak pernah menggagalkan build: tanpa konfigurasi atau tanpa Release-nya, skrip memberi tahu
# alasannya lalu keluar, dan build berjalan seperti biasa tanpa mesin Rust.
#
# Konfigurasi dibaca dari horizon-engine.properties di akar repo. Variabel lingkungan menang atasnya:
#   HORIZON_ENGINE=off                  lewati sama sekali (build tanpa jaringan)
#   HORIZON_ENGINE_COMMIT               commit sumber mesin yang dipatok (40 digit heksadesimal)
#   HORIZON_ENGINE_PROFILE              release (bawaan) atau fast
#   HORIZON_ENGINE_PATCH_SET            perubahan Amber pada sumber mesin (kosong = mesin tanpa perubahan)
#   HORIZON_ENGINE_RELEASE_REPO         pemilik/repo tempat Release mesin (bawaan: repo yang sedang membangun)
#
# Pemakaian:
#   scripts/fetch-horizon-engine.sh                   ambil dari Release dan pasang
#   scripts/fetch-horizon-engine.sh --force           pasang ulang walau sudah terpasang
#   scripts/fetch-horizon-engine.sh --abi armeabi-v7a mesin 32-bit (eksperimental; dipakai scripts/build-arm32.sh)
#   scripts/fetch-horizon-engine.sh --bundle BERKAS   pasang dari paket zip yang sudah ada di komputer
#   scripts/fetch-horizon-engine.sh --remove          hapus berkas mesin yang pernah dipasang
#
# Kode keluar: 0 selesai, sudah terpasang, atau tidak dikonfigurasi; 1 galat umum; 3 SHA-256 paket tidak cocok;
# 4 isi paket tidak sesuai; 5 Release mesin belum ada (workflow pembangunnya belum pernah dijalankan).
#
# Paket yang diunduh diperlakukan sebagai data tak tepercaya: tidak ada yang dijalankan darinya, hanya entri
# bernama pasti yang dibaca, dan tanpa SHA-256 serta commit yang cocok tidak ada yang dipasang.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAIN="$ROOT/app/src/main"
ABI="arm64-v8a"
DATA_DEST="$MAIN/assets/engine-data.zip"
STARLING_DIR="$ROOT/app/src/starling"
LICENSE_DIR="$MAIN/assets/licenses/horizon"
STAMP="$ROOT/.horizon-engine-installed"
CONFIG="$ROOT/horizon-engine.properties"
# Dua mesin berbagi skrip ini: AMBER_ENGINE=starling memasang Starling (starling-engine.properties), selain itu Horizon.
# Gradle menjalankannya sekali untuk tiap mesin (fetchHorizonEngine dan fetchStarlingEngine).
ENGINE="${AMBER_ENGINE:-horizon}"
LIB_FILE="libhorizon_android.so"
LICENSE_FALLBACK_DIR="$ROOT/third_party/horizon"
case "$ENGINE" in
  horizon) ;;
  starling)
    STAMP="$ROOT/.starling-engine-installed"
    CONFIG="$ROOT/starling-engine.properties"
    LIB_FILE="libstarling_android.so"
    LICENSE_DIR="$MAIN/assets/licenses/starling"
    LICENSE_FALLBACK_DIR="$ROOT/third_party/starling"
    ;;
  *) echo "fetch-horizon-engine: AMBER_ENGINE harus horizon atau starling." >&2; exit 1 ;;
esac
MAX_BUNDLE_BYTES=$((600 * 1024 * 1024))

say() { echo "Mesin Rust ($([ "${AMBER_ENGINE:-horizon}" = starling ] && echo Starling || echo Horizon)): $*"; }

# Berhenti dengan kode $1. Di GitHub Actions pesannya juga muncul sebagai peringatan dan di ringkasan run.
fail() {
  local code="$1"
  shift
  echo "Mesin Rust (Horizon): $*" >&2
  if [ -n "${GITHUB_ACTIONS:-}" ]; then
    echo "::warning title=Mesin Rust (Horizon) tidak dipasang::$(printf '%s' "$*" | tr '\n' ' ')"
  fi
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    {
      echo "### Mesin Rust (Horizon) tidak dipasang"
      echo
      printf '%s\n' "$*"
    } >> "$GITHUB_STEP_SUMMARY"
  fi
  exit "$code"
}

# Nilai $1 dari horizon-engine.properties (baris "kunci=nilai"); kosong bila tidak ada.
prop() {
  [ -f "$CONFIG" ] || return 0
  { tr -d '\r' < "$CONFIG" | grep -E "^[[:space:]]*$1[[:space:]]*=" || true; } \
    | tail -n 1 | sed -E 's/^[^=]*=[[:space:]]*//; s/[[:space:]]+$//'
}

# Nilai $1 dari engine.properties milik paket (berkas $2).
manifest() {
  # Pencocokan kunci harfiah (bukan regex): kunci pustaka bisa memuat '+' dan '.', mis. extra_sha256.libc++_shared.so.
  tr -d '\r' < "$2" | awk -v k="$1" 'index($0, k "=") == 1 { v = substr($0, length(k) + 2) } END { if (v != "") print v }'
}

sha256_of() {
  if command -v sha256sum > /dev/null 2>&1; then
    sha256sum "$1" | cut -d' ' -f1
  else
    shasum -a 256 "$1" | cut -d' ' -f1
  fi
}

size_of() { wc -c < "$1" | tr -d '[:space:]'; }

# 8 digit pertama hash isi perubahan Amber di folder $1 (nama dan isi tiap .diff, urut nama). HARUS sama dengan
# patch_hash() di scripts/build-horizon-engine.sh: itu yang membuat mesin yang terpasang pasti dibangun dari isi
# patch yang ada di repo ini, bukan dari versi lamanya.
patch_hash() {
  local list
  list="$(mktemp)"
  (
    cd "$1"
    export LC_ALL=C
    shopt -s nullglob
    for f in *.diff; do
      printf '%s\n' "$f"
      cat "$f"
    done
  ) > "$list"
  sha256_of "$list" | cut -c1-8
  rm -f "$list"
}

REMOVE=0
FORCE=0
BUNDLE_LOCAL=""
while [ $# -gt 0 ]; do
  case "$1" in
    --abi)
      [ $# -ge 2 ] || fail 1 "--abi butuh arm64-v8a atau armeabi-v7a."
      ABI="$2"
      shift 2
      ;;
    --bundle)
      [ $# -ge 2 ] || fail 1 "--bundle butuh jalur berkas paket."
      BUNDLE_LOCAL="$2"
      shift 2
      ;;
    --force)
      FORCE=1
      shift
      ;;
    --remove)
      REMOVE=1
      shift
      ;;
    -h | --help)
      awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"
      exit 0
      ;;
    *) fail 1 "Argumen tidak dikenal: $1" ;;
  esac
done

case "$ABI" in
  arm64-v8a | armeabi-v7a) ;;
  *) fail 1 "--abi harus arm64-v8a atau armeabi-v7a." ;;
esac
SO_DEST="$MAIN/jniLibs/$ABI/$LIB_FILE"
engine_present() {
  [ -f "$SO_DEST" ] || return 1
  if [ "$ENGINE" = starling ]; then [ -d "$STARLING_DIR/java" ]; else [ -f "$DATA_DEST" ]; fi
}
BUNDLE_NAME="$ENGINE-engine-$ABI.zip"
if [ "$ENGINE" = starling ] && [ "$ABI" != "arm64-v8a" ]; then
  say "Starling hanya tersedia untuk arm64-v8a, dilewati."
  exit 0
fi

if [ "$REMOVE" = 1 ]; then
  if [ "$ENGINE" = starling ]; then
    rm -f "$MAIN/jniLibs/arm64-v8a/$LIB_FILE" "$MAIN/jniLibs/arm64-v8a/libstarling_java.so" "$MAIN/jniLibs/arm64-v8a/libstarling_javac.so" "$STAMP"
    rm -rf "$STARLING_DIR"
  else
    rm -f "$MAIN/jniLibs/arm64-v8a/libhorizon_android.so" "$MAIN/jniLibs/armeabi-v7a/libhorizon_android.so" "$DATA_DEST" "$STAMP"
  fi
  rm -rf "$LICENSE_DIR"
  say "berkas mesin dihapus dari pohon kerja."
  exit 0
fi

if [ "${HORIZON_ENGINE:-}" = "off" ]; then
  say "dimatikan (HORIZON_ENGINE=off), dilewati."
  exit 0
fi

COMMIT=""
[ "$ENGINE" != horizon ] || COMMIT="${HORIZON_ENGINE_COMMIT:-}"
[ -n "$COMMIT" ] || COMMIT="$(prop commit)"
COMMIT="$(printf '%s' "$COMMIT" | tr 'A-F' 'a-f' | tr -d '[:space:]')"
PROFILE=""
[ "$ENGINE" != horizon ] || PROFILE="${HORIZON_ENGINE_PROFILE:-}"
[ -n "$PROFILE" ] || PROFILE="$(prop profile)"
PROFILE="${PROFILE:-release}"

if [ "$ENGINE" = horizon ]; then PATCH_SET="${HORIZON_ENGINE_PATCH_SET-$(prop patch_set)}"; else PATCH_SET="$(prop patch_set)"; fi
PATCH_SET="$(printf '%s' "$PATCH_SET" | tr 'A-Z' 'a-z' | tr -d '[:space:]')"

if [ -z "$COMMIT" ]; then
  say "tidak dikonfigurasi (commit= kosong di horizon-engine.properties), dilewati."
  exit 0
fi
[[ $COMMIT =~ ^[0-9a-f]{40}$ ]] || fail 1 "commit= harus 40 digit heksadesimal."
case "$PROFILE" in release | fast) ;; *) fail 1 "profile= harus release atau fast." ;; esac
[[ -z $PATCH_SET || $PATCH_SET =~ ^[a-z0-9]{1,20}$ ]] || fail 1 "patch_set= hanya boleh huruf kecil dan angka (paling banyak 20 karakter)."

PATCH_HASH=""
if [ -n "$PATCH_SET" ]; then
  PATCH_DIR="$ROOT/third_party/horizon-patches/$PATCH_SET"
  ls "$PATCH_DIR"/*.diff > /dev/null 2>&1 \
    || fail 1 "patch_set=$PATCH_SET tidak punya berkas .diff di third_party/horizon-patches/$PATCH_SET/."
  PATCH_HASH="$(patch_hash "$PATCH_DIR")"
fi

# Sudah terpasang untuk patokan yang sama: tidak perlu mengunduh lagi (build lokal berulang tetap cepat).
WANT_STAMP="$COMMIT $PROFILE${PATCH_SET:+ $PATCH_SET $PATCH_HASH} $ABI"
if [ "$FORCE" = 0 ] && [ -z "$BUNDLE_LOCAL" ] && engine_present \
  && [ -f "$STAMP" ] && [ "$(cat "$STAMP")" = "$WANT_STAMP" ]; then
  say "sudah terpasang ($ABI, commit ${COMMIT:0:12}, $PROFILE)."
  exit 0
fi

command -v unzip > /dev/null 2>&1 || fail 1 "perintah unzip tidak ditemukan."
command -v curl > /dev/null 2>&1 || [ -n "$BUNDLE_LOCAL" ] || fail 1 "perintah curl tidak ditemukan."

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# Tanda Release dihitung oleh skrip pembangun (--print-tag): sama persis dengan yang dipakai workflow, dan bila Starling
# aktif tandanya gabungan Horizon + Starling dalam satu Release.
TAG="$(AMBER_ENGINE_CHILD="" AMBER_ENGINE="" HORIZON_ENGINE_CONFIG="" bash "$ROOT/scripts/build-horizon-engine.sh" --print-tag 2> "$TMP/tag-err.txt")" \
  || fail 1 "tanda Release tidak bisa dihitung: $(head -n 1 "$TMP/tag-err.txt")"

BUNDLE="$TMP/$BUNDLE_NAME"
if [ -n "$BUNDLE_LOCAL" ]; then
  [ -f "$BUNDLE_LOCAL" ] || fail 1 "paket tidak ditemukan: $BUNDLE_LOCAL"
  cp "$BUNDLE_LOCAL" "$BUNDLE"
  WANT_SHA=""
else
  # Tempat mencari Release, berurutan: variabel lingkungan, repo yang sedang membangun, lalu release_repo=.
  REPOS=()
  for r in "${HORIZON_ENGINE_RELEASE_REPO:-}" "${GITHUB_REPOSITORY:-}" "$(prop release_repo)"; do
    r="$(printf '%s' "$r" | tr -d '[:space:]')"
    [ -n "$r" ] || continue
    [[ $r =~ ^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$ ]] || fail 1 "nama repo Release tidak valid: $r"
    dup=0
    for seen in ${REPOS[@]+"${REPOS[@]}"}; do [ "$seen" = "$r" ] && dup=1; done
    [ "$dup" = 1 ] || REPOS+=("$r")
  done
  [ "${#REPOS[@]}" -gt 0 ] || fail 1 "tidak ada repo untuk mencari Release mesin (isi release_repo= di horizon-engine.properties)."

  # HORIZON_ENGINE_BASE_URL hanya untuk pengujian dan hanya diterima untuk alamat lokal.
  BASE_OVERRIDE="${HORIZON_ENGINE_BASE_URL:-}"
  PROTO='=https'
  if [ -n "$BASE_OVERRIDE" ]; then
    case "$BASE_OVERRIDE" in
      http://127.0.0.1:* | http://localhost:*) PROTO='=https,http' ;;
      *) fail 1 "HORIZON_ENGINE_BASE_URL hanya boleh alamat lokal (http://127.0.0.1:PORT)." ;;
    esac
  fi

  FOUND=""
  TRIED=""
  for repo in "${REPOS[@]}"; do
    if [ -n "$BASE_OVERRIDE" ]; then
      URL_BASE="$BASE_OVERRIDE/$TAG"
    else
      URL_BASE="https://github.com/$repo/releases/download/$TAG"
    fi
    TRIED="$TRIED $repo"
    code="$(curl --silent --show-error --location --retry 2 --connect-timeout 30 --max-time 120 \
      --proto "$PROTO" --proto-redir "$PROTO" --output "$TMP/sha256.txt" --write-out '%{http_code}' \
      "$URL_BASE/$BUNDLE_NAME.sha256" 2> "$TMP/curl.err" || true)"
    case "$code" in
      200)
        FOUND="$URL_BASE"
        break
        ;;
      404) continue ;;
      *) fail 1 "tidak bisa membaca $URL_BASE/$BUNDLE_NAME.sha256 (HTTP $code). $(tr '\n' ' ' < "$TMP/curl.err")" ;;
    esac
  done
  if [ -z "$FOUND" ]; then
    fail 5 "Release mesin belum ada, jadi mesin TIDAK dipasang (build tetap jalan tanpa mesin Rust).

Dicari di:$TRIED
Tag yang dicari: $TAG (berkas $BUNDLE_NAME)

Di GitHub Actions, workflow \"Build APK\" membangun mesin ini otomatis di run yang sama bila Release-nya belum ada (kalau tidak ada, run itu pasti gagal membangunnya: lihat job \"Bangun mesin Rust (Horizon)\"). Cara manual: tab Actions > \"Bangun mesin Rust (Horizon)\" > Run workflow. Panduan: docs/rust-engine.md"
  fi

  WANT_SHA="$(tr -d '\r' < "$TMP/sha256.txt" | head -n 1 | cut -d' ' -f1 | tr 'A-F' 'a-f')"
  [[ $WANT_SHA =~ ^[0-9a-f]{64}$ ]] || fail 4 "berkas $BUNDLE_NAME.sha256 di Release tidak berisi SHA-256 yang sah."

  say "mengunduh paket mesin dari $FOUND ..."
  curl --fail --silent --show-error --location --retry 3 --connect-timeout 30 --max-time 900 \
    --proto "$PROTO" --proto-redir "$PROTO" --max-filesize "$MAX_BUNDLE_BYTES" \
    --output "$BUNDLE" "$FOUND/$BUNDLE_NAME" || fail 1 "unduhan paket gagal. Coba jalankan ulang."
fi

[ "$(size_of "$BUNDLE")" -le "$MAX_BUNDLE_BYTES" ] || fail 4 "paket lebih besar dari $((MAX_BUNDLE_BYTES / 1024 / 1024)) MB, ditolak."

HAVE="$(sha256_of "$BUNDLE")"
if [ -n "$WANT_SHA" ] && [ "$HAVE" != "$WANT_SHA" ]; then
  fail 3 "SHA-256 paket tidak cocok dengan yang tercatat di Release, mesin TIDAK dipasang.

Tercatat: $WANT_SHA
Didapat:  $HAVE

Unduhan mungkin rusak: coba lagi. Bila tetap begitu, jalankan ulang workflow pembangun mesin dengan force."
fi

unzip -tq "$BUNDLE" > /dev/null 2>&1 || fail 4 "paket mesin bukan zip yang utuh."
# Hanya entri bernama pasti yang dibaca, ke berkas sementara.
extract() { unzip -p "$BUNDLE" "$1" > "$2" 2> /dev/null && [ -s "$2" ]; }
extract engine.properties "$TMP/engine.properties" || fail 4 "paket tidak berisi engine.properties."
M="$TMP/engine.properties"
# Schema 2 = mesin Starling (tanpa tabel data; pustaka pendamping, kelas Java, resource, dan aset dalam satu commit).
if [ "$(manifest schema "$M")" = "2" ]; then
  [ "$ENGINE" = starling ] || fail 4 "paket schema 2 hanya untuk mesin Starling."
  extract libhorizon_android.so "$TMP/lib.so" || fail 4 "paket tidak berisi libhorizon_android.so."
  extract starling-files.zip "$TMP/files.zip" || fail 4 "paket mesin Starling tidak berisi starling-files.zip."
  [ "$(manifest abi "$M")" = "$ABI" ] || fail 4 "paket bukan untuk $ABI."
  [ "$(manifest source_commit "$M")" = "$COMMIT" ] \
    || fail 4 "paket dibangun dari commit lain ($(manifest source_commit "$M")), bukan yang dipatok ($COMMIT)."
  [ "$(manifest patch_set "$M")" = "$PATCH_SET" ] \
    || fail 4 "paket memakai perubahan sumber '$(manifest patch_set "$M")', bukan '$PATCH_SET' yang diminta."
  [ "$(manifest patch_hash "$M")" = "$PATCH_HASH" ] \
    || fail 4 "paket dibangun dari isi patch '$(manifest patch_hash "$M")', bukan '$PATCH_HASH' yang ada di repo ini. Bangun ulang mesinnya."
  [ "$(manifest so_sha256 "$M")" = "$(sha256_of "$TMP/lib.so")" ] || fail 4 "libhorizon_android.so tidak cocok dengan catatan di paket."
  [ "$(manifest files_sha256 "$M")" = "$(sha256_of "$TMP/files.zip")" ] || fail 4 "starling-files.zip tidak cocok dengan catatan di paket."
  elf_ok() { # $1 berkas: ELF 64-bit arm64 little-endian
    local h
    h="$(od -An -v -tx1 -N20 "$1" | tr -d ' \n')"
    [ "${h:0:10}" = "7f454c4602" ] && [ "${h:10:2}" = "01" ] && [ "${h:36:4}" = "b700" ]
  }
  [ "$ABI" = "arm64-v8a" ] || fail 4 "mesin Starling hanya tersedia untuk arm64-v8a."
  elf_ok "$TMP/lib.so" || fail 4 "libhorizon_android.so bukan pustaka ELF 64-bit arm64 (AArch64)."
  unzip -tq "$TMP/files.zip" > /dev/null 2>&1 || fail 4 "starling-files.zip di paket rusak (bukan zip yang utuh)."
  # Isi zip dianggap tak tepercaya: hanya java/, res/, assets/ dengan nama aman yang boleh diekstrak.
  BAD="$(unzip -Z1 "$TMP/files.zip" | grep -Ev '^(java|res|assets)/[A-Za-z0-9_+./-]*$' || true)"
  BAD="$BAD$(unzip -Z1 "$TMP/files.zip" | grep -E '(^|/)\.\.(/|$)' || true)"
  [ -z "$BAD" ] || fail 4 "starling-files.zip berisi nama berkas yang tidak diizinkan: $(printf '%s' "$BAD" | head -n 3 | tr '\n' ' ')"
  for f in LICENSE THIRD_PARTY_NOTICES.md; do
    if ! extract "$f" "$TMP/$f"; then
      [ -f "$LICENSE_FALLBACK_DIR/$f" ] || fail 4 "pemberitahuan lisensi $f tidak ada di paket maupun di third_party/horizon, mesin tidak dipasang."
      cp "$LICENSE_FALLBACK_DIR/$f" "$TMP/$f"
    fi
  done
  EXTRA_LIST="$(manifest extras "$M")"
  for n in $EXTRA_LIST; do
    [[ $n =~ ^[A-Za-z0-9_+.-]+\.so$ ]] || fail 4 "nama pustaka pendamping tidak sah: $n"
    extract "$n" "$TMP/x_$n" || fail 4 "paket tidak berisi $n."
    [ "$(manifest "extra_sha256.$n" "$M")" = "$(sha256_of "$TMP/x_$n")" ] || fail 4 "$n tidak cocok dengan catatan di paket."
    elf_ok "$TMP/x_$n" || fail 4 "$n bukan pustaka ELF 64-bit arm64."
  done
  mkdir -p "$(dirname "$SO_DEST")" "$LICENSE_DIR"
  cp "$TMP/lib.so" "$SO_DEST"
  for n in $EXTRA_LIST; do
    # libc++_shared.so sudah dibawa pustaka lain di APK ini: yang ada dipertahankan.
    if [ "$n" = "libc++_shared.so" ] && [ -f "$(dirname "$SO_DEST")/$n" ]; then continue; fi
    cp "$TMP/x_$n" "$(dirname "$SO_DEST")/$n"
    chmod 644 "$(dirname "$SO_DEST")/$n"
  done
  rm -rf "$STARLING_DIR"
  mkdir -p "$STARLING_DIR"
  unzip -q -o "$TMP/files.zip" -d "$STARLING_DIR"
  cp "$TMP/LICENSE" "$TMP/THIRD_PARTY_NOTICES.md" "$LICENSE_DIR/"
  chmod 644 "$SO_DEST"
  printf '%s\n' "$WANT_STAMP" > "$STAMP"
  say "mesin Starling terpasang ($ABI, commit ${COMMIT:0:12}, $PROFILE${PATCH_SET:+, perubahan Amber: $PATCH_SET})."
  say "  $LIB_FILE -> $(size_of "$SO_DEST") byte, pustaka pendamping: ${EXTRA_LIST:-tidak ada}"
  say "  java/res/aset -> app/src/starling ($(find "$STARLING_DIR" -type f | wc -l | tr -d ' ') berkas)"
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    {
      echo "### Mesin Rust (Starling) ikut dibangun"
      echo
      echo "- Sumber: \`$(manifest source_repo "$M")\` commit \`${COMMIT:0:12}\` ($PROFILE)${PATCH_SET:+, perubahan Amber: $PATCH_SET}"
      echo "- \`libhorizon_android.so\`: $(size_of "$SO_DEST") byte; pustaka pendamping: ${EXTRA_LIST:-tidak ada}"
      echo "- Kelas Java, resource, dan aset dipasang ke \`app/src/starling\`"
    } >> "$GITHUB_STEP_SUMMARY"
  fi
  exit 0
fi
extract libhorizon_android.so "$TMP/lib.so" || fail 4 "paket tidak berisi libhorizon_android.so."
extract engine-data.zip "$TMP/engine-data.zip" || fail 4 "paket tidak berisi engine-data.zip."

[ "$ENGINE" = horizon ] || fail 4 "paket schema 1 hanya untuk mesin Horizon."
[ "$(manifest schema "$M")" = "1" ] || fail 4 "format paket tidak dikenal (schema bukan 1)."
[ "$(manifest abi "$M")" = "$ABI" ] || fail 4 "paket bukan untuk $ABI."
[ "$(manifest source_commit "$M")" = "$COMMIT" ] \
  || fail 4 "paket dibangun dari commit lain ($(manifest source_commit "$M")), bukan yang dipatok ($COMMIT)."
[ "$(manifest patch_set "$M")" = "$PATCH_SET" ] \
  || fail 4 "paket memakai perubahan sumber '$(manifest patch_set "$M")', bukan '$PATCH_SET' yang diminta."
[ "$(manifest patch_hash "$M")" = "$PATCH_HASH" ] \
  || fail 4 "paket dibangun dari isi patch '$(manifest patch_hash "$M")', bukan '$PATCH_HASH' yang ada di repo ini. Bangun ulang mesinnya."
[ "$(manifest so_sha256 "$M")" = "$(sha256_of "$TMP/lib.so")" ] || fail 4 "libhorizon_android.so tidak cocok dengan catatan di paket."
[ "$(manifest data_sha256 "$M")" = "$(sha256_of "$TMP/engine-data.zip")" ] || fail 4 "engine-data.zip tidak cocok dengan catatan di paket."

# Kepala ELF little-endian: 7f 45 4c 46, kelas (01 = 32-bit, 02 = 64-bit), data 01, dan e_machine (byte 18-19):
# b7 00 = AArch64 (arm64), 28 00 = ARM (armeabi-v7a).
HEADER="$(od -An -v -tx1 -N20 "$TMP/lib.so" | tr -d ' \n')"
if [ "$ABI" = "arm64-v8a" ]; then
  WANT_ELF="7f454c4602" WANT_MACHINE="b700" WANT_DESC="ELF 64-bit arm64 (AArch64)"
else
  WANT_ELF="7f454c4601" WANT_MACHINE="2800" WANT_DESC="ELF 32-bit ARM (armeabi-v7a)"
fi
if [ "${HEADER:0:10}" != "$WANT_ELF" ] || [ "${HEADER:10:2}" != "01" ] || [ "${HEADER:36:4}" != "$WANT_MACHINE" ]; then
  fail 4 "libhorizon_android.so bukan pustaka $WANT_DESC."
fi
unzip -tq "$TMP/engine-data.zip" > /dev/null 2>&1 || fail 4 "engine-data.zip di paket rusak (bukan zip yang utuh)."

# Pemberitahuan lisensi MIT wajib ikut bila mesin dikemas: dari paket (sesuai sumber yang dikompilasi),
# atau salinan di third_party/horizon bila paket tidak membawanya.
for f in LICENSE THIRD_PARTY_NOTICES.md; do
  if ! extract "$f" "$TMP/$f"; then
    [ -f "$LICENSE_FALLBACK_DIR/$f" ] || fail 4 "pemberitahuan lisensi $f tidak ada di paket maupun di third_party/horizon, mesin tidak dipasang."
    cp "$LICENSE_FALLBACK_DIR/$f" "$TMP/$f"
  fi
done

mkdir -p "$(dirname "$SO_DEST")" "$(dirname "$DATA_DEST")" "$LICENSE_DIR"
cp "$TMP/lib.so" "$SO_DEST"
cp "$TMP/engine-data.zip" "$DATA_DEST"
cp "$TMP/LICENSE" "$TMP/THIRD_PARTY_NOTICES.md" "$LICENSE_DIR/"
chmod 644 "$SO_DEST" "$DATA_DEST"
printf '%s\n' "$WANT_STAMP" > "$STAMP"

say "terpasang ($ABI, commit ${COMMIT:0:12}, $PROFILE${PATCH_SET:+, perubahan Amber: $PATCH_SET})."
say "  libhorizon_android.so -> $(size_of "$SO_DEST") byte, sha256 $(sha256_of "$SO_DEST")"
say "  engine-data.zip -> $(size_of "$DATA_DEST") byte, sha256 $(sha256_of "$DATA_DEST")"
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  {
    echo "### Mesin Rust (Horizon) ikut dibangun"
    echo
    echo "- Sumber: \`$(manifest source_repo "$M")\` commit \`${COMMIT:0:12}\` ($PROFILE)${PATCH_SET:+, perubahan Amber: $PATCH_SET}"
    echo "- ABI: \`$ABI\`"
    echo "- \`libhorizon_android.so\`: $(size_of "$SO_DEST") byte"
    echo "- \`engine-data.zip\`: $(size_of "$DATA_DEST") byte"
  } >> "$GITHUB_STEP_SUMMARY"
fi
