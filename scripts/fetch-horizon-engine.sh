#!/usr/bin/env bash
# Memasang mesin Rust (Horizon) ke pohon kerja dari Release repo ini sendiri, lalu menaruh dua berkas yang
# dikemas Gradle:
#   app/src/main/jniLibs/arm64-v8a/libhorizon_android.so   mesinnya (arm64)
#   app/src/main/assets/engine-data.zip                     tabel data mesin
# beserta pemberitahuan lisensi MIT-nya di app/src/main/assets/licenses/horizon/.
# Semuanya ada di .gitignore dan tidak pernah disimpan ke repo.
#
# Mesinnya dikompilasi dari sumber oleh workflow "Bangun mesin Rust (Horizon)" (scripts/build-horizon-engine.sh)
# dan diterbitkan sebagai Release bertanda horizon-engine-<12 digit commit>[-<patch_set>][-fast]. Skrip ini hanya
# mengambil hasilnya.
# Dipanggil otomatis oleh Gradle sebelum build (app/build.gradle.kts, tugas fetchHorizonEngine).
#
# OPSIONAL dan tidak pernah menggagalkan build: tanpa konfigurasi atau tanpa Release-nya, skrip memberi tahu
# alasannya lalu keluar, dan build berjalan seperti biasa tanpa mesin Rust.
#
# Konfigurasi dibaca dari horizon-engine.properties di akar repo. Variabel lingkungan menang atasnya:
#   HORIZON_ENGINE=off                  lewati sama sekali (build 32-bit, atau build tanpa jaringan)
#   HORIZON_ENGINE_COMMIT               commit sumber mesin yang dipatok (40 digit heksadesimal)
#   HORIZON_ENGINE_PROFILE              release (bawaan) atau fast
#   HORIZON_ENGINE_PATCH_SET            perubahan Amber pada sumber mesin (kosong = mesin tanpa perubahan)
#   HORIZON_ENGINE_RELEASE_REPO         pemilik/repo tempat Release mesin (bawaan: repo yang sedang membangun)
#
# Pemakaian:
#   scripts/fetch-horizon-engine.sh                   ambil dari Release dan pasang
#   scripts/fetch-horizon-engine.sh --force           pasang ulang walau sudah terpasang
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
SO_DEST="$MAIN/jniLibs/arm64-v8a/libhorizon_android.so"
DATA_DEST="$MAIN/assets/engine-data.zip"
LICENSE_DIR="$MAIN/assets/licenses/horizon"
LICENSE_FALLBACK="$ROOT/third_party/horizon"
STAMP="$ROOT/.horizon-engine-installed"
CONFIG="$ROOT/horizon-engine.properties"
BUNDLE_NAME="horizon-engine-arm64-v8a.zip"
MAX_BUNDLE_BYTES=$((600 * 1024 * 1024))

say() { echo "Mesin Rust (Horizon): $*"; }

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
  { tr -d '\r' < "$2" | grep -E "^$1=" || true; } | tail -n 1 | sed -E 's/^[^=]*=//'
}

sha256_of() {
  if command -v sha256sum > /dev/null 2>&1; then
    sha256sum "$1" | cut -d' ' -f1
  else
    shasum -a 256 "$1" | cut -d' ' -f1
  fi
}

size_of() { wc -c < "$1" | tr -d '[:space:]'; }

REMOVE=0
FORCE=0
BUNDLE_LOCAL=""
while [ $# -gt 0 ]; do
  case "$1" in
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

if [ "$REMOVE" = 1 ]; then
  rm -f "$SO_DEST" "$DATA_DEST" "$STAMP"
  rm -rf "$LICENSE_DIR"
  say "berkas mesin dihapus dari pohon kerja."
  exit 0
fi

if [ "${HORIZON_ENGINE:-}" = "off" ]; then
  say "dimatikan (HORIZON_ENGINE=off), dilewati."
  exit 0
fi

COMMIT="${HORIZON_ENGINE_COMMIT:-}"
[ -n "$COMMIT" ] || COMMIT="$(prop commit)"
COMMIT="$(printf '%s' "$COMMIT" | tr 'A-F' 'a-f' | tr -d '[:space:]')"
PROFILE="${HORIZON_ENGINE_PROFILE:-}"
[ -n "$PROFILE" ] || PROFILE="$(prop profile)"
PROFILE="${PROFILE:-release}"

PATCH_SET="${HORIZON_ENGINE_PATCH_SET-$(prop patch_set)}"
PATCH_SET="$(printf '%s' "$PATCH_SET" | tr 'A-Z' 'a-z' | tr -d '[:space:]')"

if [ -z "$COMMIT" ]; then
  say "tidak dikonfigurasi (commit= kosong di horizon-engine.properties), dilewati."
  exit 0
fi
[[ $COMMIT =~ ^[0-9a-f]{40}$ ]] || fail 1 "commit= harus 40 digit heksadesimal."
case "$PROFILE" in release | fast) ;; *) fail 1 "profile= harus release atau fast." ;; esac
[[ -z $PATCH_SET || $PATCH_SET =~ ^[a-z0-9]{1,20}$ ]] || fail 1 "patch_set= hanya boleh huruf kecil dan angka (paling banyak 20 karakter)."

# Sudah terpasang untuk patokan yang sama: tidak perlu mengunduh lagi (build lokal berulang tetap cepat).
WANT_STAMP="$COMMIT $PROFILE${PATCH_SET:+ $PATCH_SET}"
if [ "$FORCE" = 0 ] && [ -z "$BUNDLE_LOCAL" ] && [ -f "$SO_DEST" ] && [ -f "$DATA_DEST" ] \
  && [ -f "$STAMP" ] && [ "$(cat "$STAMP")" = "$WANT_STAMP" ]; then
  say "sudah terpasang (commit ${COMMIT:0:12}, $PROFILE)."
  exit 0
fi

command -v unzip > /dev/null 2>&1 || fail 1 "perintah unzip tidak ditemukan."
command -v curl > /dev/null 2>&1 || [ -n "$BUNDLE_LOCAL" ] || fail 1 "perintah curl tidak ditemukan."

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

TAG="horizon-engine-${COMMIT:0:12}${PATCH_SET:+-$PATCH_SET}"
[ "$PROFILE" = "release" ] || TAG="$TAG-$PROFILE"

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
Tag yang dicari: $TAG

Jalankan sekali workflow \"Bangun mesin Rust (Horizon)\" (tab Actions > Run workflow). Hasilnya diterbitkan sebagai Release bertanda itu, dan build APK berikutnya memasangnya otomatis. Panduan: docs/rust-engine.md"
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
extract libhorizon_android.so "$TMP/lib.so" || fail 4 "paket tidak berisi libhorizon_android.so."
extract engine-data.zip "$TMP/engine-data.zip" || fail 4 "paket tidak berisi engine-data.zip."

M="$TMP/engine.properties"
[ "$(manifest schema "$M")" = "1" ] || fail 4 "format paket tidak dikenal (schema bukan 1)."
[ "$(manifest abi "$M")" = "arm64-v8a" ] || fail 4 "paket bukan untuk arm64-v8a."
[ "$(manifest source_commit "$M")" = "$COMMIT" ] \
  || fail 4 "paket dibangun dari commit lain ($(manifest source_commit "$M")), bukan yang dipatok ($COMMIT)."
[ "$(manifest patch_set "$M")" = "$PATCH_SET" ] \
  || fail 4 "paket memakai perubahan sumber '$(manifest patch_set "$M")', bukan '$PATCH_SET' yang diminta."
[ "$(manifest so_sha256 "$M")" = "$(sha256_of "$TMP/lib.so")" ] || fail 4 "libhorizon_android.so tidak cocok dengan catatan di paket."
[ "$(manifest data_sha256 "$M")" = "$(sha256_of "$TMP/engine-data.zip")" ] || fail 4 "engine-data.zip tidak cocok dengan catatan di paket."

# Pustaka harus ELF 64-bit little-endian untuk arm64: byte 0-3 = 7f 45 4c 46, byte 4 = 02, byte 5 = 01,
# dan e_machine (byte 18-19) = b7 00 (AArch64).
HEADER="$(od -An -v -tx1 -N20 "$TMP/lib.so" | tr -d ' \n')"
if [ "${HEADER:0:10}" != "7f454c4602" ] || [ "${HEADER:10:2}" != "01" ] || [ "${HEADER:36:4}" != "b700" ]; then
  fail 4 "libhorizon_android.so bukan pustaka ELF 64-bit arm64 (AArch64)."
fi
unzip -tq "$TMP/engine-data.zip" > /dev/null 2>&1 || fail 4 "engine-data.zip di paket rusak (bukan zip yang utuh)."

# Pemberitahuan lisensi MIT wajib ikut bila mesin dikemas: dari paket (sesuai sumber yang dikompilasi),
# atau salinan di third_party/horizon bila paket tidak membawanya.
for f in LICENSE THIRD_PARTY_NOTICES.md; do
  if ! extract "$f" "$TMP/$f"; then
    [ -f "$LICENSE_FALLBACK/$f" ] || fail 4 "pemberitahuan lisensi $f tidak ada di paket maupun di third_party/horizon, mesin tidak dipasang."
    cp "$LICENSE_FALLBACK/$f" "$TMP/$f"
  fi
done

mkdir -p "$(dirname "$SO_DEST")" "$(dirname "$DATA_DEST")" "$LICENSE_DIR"
cp "$TMP/lib.so" "$SO_DEST"
cp "$TMP/engine-data.zip" "$DATA_DEST"
cp "$TMP/LICENSE" "$TMP/THIRD_PARTY_NOTICES.md" "$LICENSE_DIR/"
chmod 644 "$SO_DEST" "$DATA_DEST"
printf '%s\n' "$WANT_STAMP" > "$STAMP"

say "terpasang (commit ${COMMIT:0:12}, $PROFILE${PATCH_SET:+, perubahan Amber: $PATCH_SET})."
say "  libhorizon_android.so -> $(size_of "$SO_DEST") byte, sha256 $(sha256_of "$SO_DEST")"
say "  engine-data.zip -> $(size_of "$DATA_DEST") byte, sha256 $(sha256_of "$DATA_DEST")"
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  {
    echo "### Mesin Rust (Horizon) ikut dibangun"
    echo
    echo "- Sumber: \`$(manifest source_repo "$M")\` commit \`${COMMIT:0:12}\` ($PROFILE)${PATCH_SET:+, perubahan Amber: $PATCH_SET}"
    echo "- \`libhorizon_android.so\`: $(size_of "$SO_DEST") byte"
    echo "- \`engine-data.zip\`: $(size_of "$DATA_DEST") byte"
  } >> "$GITHUB_STEP_SUMMARY"
fi
