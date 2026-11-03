#!/usr/bin/env bash
# Mengambil mesin Rust (Horizon) dari APK Horizon Android, lalu menaruh dua berkas yang dikemas Gradle:
#   app/src/main/jniLibs/arm64-v8a/libhorizon_android.so   mesinnya (arm64)
#   app/src/main/assets/engine-data.zip                     tabel data mesin
# beserta pemberitahuan lisensi MIT-nya di app/src/main/assets/licenses/horizon/.
# Semuanya ada di .gitignore dan tidak pernah disimpan ke repo.
#
# OPSIONAL: tanpa konfigurasi skrip ini keluar dengan sukses tanpa mengubah apa pun (build biasa).
#
# Konfigurasi. Variabel lingkungan menang atas berkas horizon-engine.properties di akar repo:
#   HORIZON_ENGINE_URL     alamat unduhan APK Horizon Android (arm64), harus https://
#   HORIZON_ENGINE_SHA256  SHA-256 APK itu (64 digit heksadesimal). WAJIB. Tanpa ini tidak ada yang dipasang:
#                          skrip hanya mencetak SHA-256 hasil unduhan supaya bisa kamu periksa dan, bila
#                          kamu percaya berkasnya, kamu salin ke konfigurasi.
#
# Pemakaian:
#   scripts/fetch-horizon-engine.sh                baca konfigurasi seperti di atas
#   scripts/fetch-horizon-engine.sh --apk BERKAS   pakai APK yang sudah ada di komputer (SHA-256 tetap wajib)
#   scripts/fetch-horizon-engine.sh --remove       hapus berkas mesin yang pernah dipasang
#
# Kode keluar: 0 selesai atau tidak dikonfigurasi, 1 galat umum, 2 SHA-256 belum diisi,
# 3 SHA-256 tidak cocok, 4 isi APK tidak sesuai.
#
# APK yang diunduh diperlakukan sebagai data tak tepercaya: tidak ada yang dijalankan darinya, hanya dua entri
# bernama pasti yang dibaca, dan tanpa SHA-256 yang cocok tidak ada yang dipasang.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAIN="$ROOT/app/src/main"
SO_DEST="$MAIN/jniLibs/arm64-v8a/libhorizon_android.so"
DATA_DEST="$MAIN/assets/engine-data.zip"
LICENSE_DIR="$MAIN/assets/licenses/horizon"
LICENSE_SRC="$ROOT/third_party/horizon"
CONFIG="$ROOT/horizon-engine.properties"
SO_ENTRY="lib/arm64-v8a/libhorizon_android.so"
DATA_ENTRY="assets/engine-data.zip"
MAX_APK_BYTES=$((400 * 1024 * 1024))

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

sha256_of() {
  if command -v sha256sum > /dev/null 2>&1; then
    sha256sum "$1" | cut -d' ' -f1
  else
    shasum -a 256 "$1" | cut -d' ' -f1
  fi
}

size_of() { wc -c < "$1" | tr -d '[:space:]'; }

REMOVE=0
APK_LOCAL=""
while [ $# -gt 0 ]; do
  case "$1" in
    --apk)
      [ $# -ge 2 ] || fail 1 "--apk butuh jalur berkas APK."
      APK_LOCAL="$2"
      shift 2
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
  rm -f "$SO_DEST" "$DATA_DEST"
  rm -rf "$LICENSE_DIR"
  say "berkas mesin dihapus dari pohon kerja."
  exit 0
fi

URL="${HORIZON_ENGINE_URL:-}"
[ -n "$URL" ] || URL="$(prop url)"
WANT="${HORIZON_ENGINE_SHA256:-}"
[ -n "$WANT" ] || WANT="$(prop sha256)"
WANT="$(printf '%s' "$WANT" | tr 'A-F' 'a-f' | tr -d '[:space:]')"

if [ -z "$APK_LOCAL" ] && [ -z "$URL" ]; then
  say "tidak dikonfigurasi, dilewati (build tanpa mesin Rust)."
  exit 0
fi

if [ -n "$WANT" ] && ! [[ $WANT =~ ^[0-9a-f]{64}$ ]]; then
  fail 1 "SHA-256 harus 64 digit heksadesimal (0-9, a-f)."
fi

# Pemberitahuan lisensi wajib ikut bila mesin dikemas (MIT mensyaratkannya): periksa sebelum mengunduh apa pun.
for f in LICENSE THIRD_PARTY_NOTICES.md; do
  [ -f "$LICENSE_SRC/$f" ] || fail 1 "berkas lisensi third_party/horizon/$f tidak ada, mesin tidak dipasang."
done
command -v unzip > /dev/null 2>&1 || fail 1 "perintah unzip tidak ditemukan."

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

if [ -n "$APK_LOCAL" ]; then
  [ -f "$APK_LOCAL" ] || fail 1 "berkas APK tidak ditemukan: $APK_LOCAL"
  APK="$APK_LOCAL"
else
  case "$URL" in
    https://*) ;;
    *) fail 1 "alamat unduhan harus diawali https:// (HORIZON_ENGINE_URL atau url= di horizon-engine.properties)." ;;
  esac
  APK="$TMP/horizon-android.apk"
  say "mengunduh APK Horizon Android ..."
  curl --fail --silent --show-error --location --retry 3 --connect-timeout 30 --max-time 900 \
    --proto '=https' --proto-redir '=https' --max-filesize "$MAX_APK_BYTES" \
    --output "$APK" "$URL" || fail 1 "unduhan gagal. Periksa alamatnya (url=) dan coba lagi."
fi

[ "$(size_of "$APK")" -le "$MAX_APK_BYTES" ] || fail 1 "APK lebih besar dari $((MAX_APK_BYTES / 1024 / 1024)) MB, ditolak."

HAVE="$(sha256_of "$APK")"
if [ -z "$WANT" ]; then
  fail 2 "SHA-256 belum diisi, jadi mesin TIDAK dipasang.

SHA-256 APK yang baru diunduh:

    $HAVE

Bandingkan dengan SHA256SUMS.txt di halaman rilis Horizon Android. Bila kamu percaya berkas ini, isi sha256= di horizon-engine.properties dengan nilai itu."
fi
if [ "$HAVE" != "$WANT" ]; then
  fail 3 "SHA-256 tidak cocok, mesin TIDAK dipasang.

Diharapkan: $WANT
Didapat:    $HAVE

Berkas di alamat itu berbeda dari yang kamu patok (mungkin rilis diganti)."
fi

# Hanya dua entri bernama pasti yang dibaca, ke berkas sementara.
extract() { unzip -p "$APK" "$1" > "$2" 2> /dev/null && [ -s "$2" ]; }
extract "$SO_ENTRY" "$TMP/lib.so" || fail 4 "APK tidak berisi $SO_ENTRY."
extract "$DATA_ENTRY" "$TMP/engine-data.zip" || fail 4 "APK tidak berisi $DATA_ENTRY."

# Pustaka harus ELF 64-bit little-endian untuk arm64: byte 0-3 = 7f 45 4c 46, byte 4 = 02, byte 5 = 01,
# dan e_machine (byte 18-19) = b7 00 (AArch64).
HEADER="$(od -An -v -tx1 -N20 "$TMP/lib.so" | tr -d ' \n')"
if [ "${HEADER:0:10}" != "7f454c4602" ] || [ "${HEADER:10:2}" != "01" ] || [ "${HEADER:36:4}" != "b700" ]; then
  fail 4 "$SO_ENTRY bukan pustaka ELF 64-bit arm64 (AArch64)."
fi
unzip -tq "$TMP/engine-data.zip" > /dev/null 2>&1 || fail 4 "$DATA_ENTRY di APK rusak (bukan zip yang utuh)."

mkdir -p "$(dirname "$SO_DEST")" "$(dirname "$DATA_DEST")" "$LICENSE_DIR"
cp "$TMP/lib.so" "$SO_DEST"
cp "$TMP/engine-data.zip" "$DATA_DEST"
cp "$LICENSE_SRC/LICENSE" "$LICENSE_SRC/THIRD_PARTY_NOTICES.md" "$LICENSE_DIR/"
chmod 644 "$SO_DEST" "$DATA_DEST"

say "terpasang."
say "  $SO_ENTRY -> $(size_of "$SO_DEST") byte, sha256 $(sha256_of "$SO_DEST")"
say "  $DATA_ENTRY -> $(size_of "$DATA_DEST") byte, sha256 $(sha256_of "$DATA_DEST")"
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  {
    echo "### Mesin Rust (Horizon) ikut dibangun"
    echo
    echo "- APK sumber, SHA-256: \`$HAVE\` (cocok dengan patokan)"
    echo "- \`libhorizon_android.so\`: $(size_of "$SO_DEST") byte"
    echo "- \`engine-data.zip\`: $(size_of "$DATA_DEST") byte"
  } >> "$GITHUB_STEP_SUMMARY"
fi
