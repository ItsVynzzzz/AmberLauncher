#!/usr/bin/env bash
# Mengemas mesin Rust (Horizon) yang sudah dikompilasi menjadi satu paket zip untuk Release repo ini.
# Dipanggil oleh scripts/build-horizon-engine.sh; dipisah supaya bisa diuji tanpa mengompilasi apa pun.
#
# Isi paket (nama berkas tetap, dibaca scripts/fetch-horizon-engine.sh):
#   libhorizon_android.so        mesinnya (arm64, sudah di-strip)
#   engine-data.zip              tabel data mesin hasil generate
#   LICENSE, THIRD_PARTY_NOTICES.md   pemberitahuan lisensi MIT dari sumber yang dikompilasi
#   engine.properties            keterangan: sumber, commit, perubahan Amber (patch_set dan hash isinya), versi alat, ukuran dan SHA-256 tiap berkas
#
# Pemakaian:
#   package-horizon-engine.sh --so BERKAS --data BERKAS --license-dir FOLDER --out FOLDER \
#       --repo URL --commit SHA40 --profile release|fast [--patch-set NAMA --patch-hash HEX8] [--rust VERSI] [--ndk VERSI] [--readelf ALAT]
#
# Hasil: FOLDER/horizon-engine-arm64-v8a.zip dan FOLDER/horizon-engine-arm64-v8a.zip.sha256

set -euo pipefail

BUNDLE_NAME="horizon-engine-arm64-v8a.zip"

die() { echo "package-horizon-engine: $*" >&2; exit 1; }

SO="" DATA="" LICENSE_DIR="" OUT="" REPO="" COMMIT="" PROFILE="" PATCH_SET="" PATCH_HASH="" RUST="" NDK="" READELF=""
while [ $# -gt 0 ]; do
  case "$1" in
    --so | --data | --license-dir | --out | --repo | --commit | --profile | --patch-set | --patch-hash | --rust | --ndk | --readelf)
      [ $# -ge 2 ] || die "$1 butuh nilai."
      case "$1" in
        --so) SO="$2" ;;
        --data) DATA="$2" ;;
        --license-dir) LICENSE_DIR="$2" ;;
        --out) OUT="$2" ;;
        --repo) REPO="$2" ;;
        --commit) COMMIT="$2" ;;
        --profile) PROFILE="$2" ;;
        --patch-set) PATCH_SET="$2" ;;
        --patch-hash) PATCH_HASH="$2" ;;
        --rust) RUST="$2" ;;
        --ndk) NDK="$2" ;;
        --readelf) READELF="$2" ;;
      esac
      shift 2
      ;;
    -h | --help)
      awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"
      exit 0
      ;;
    *) die "argumen tidak dikenal: $1" ;;
  esac
done

for v in SO DATA LICENSE_DIR OUT REPO COMMIT PROFILE; do
  [ -n "${!v}" ] || die "argumen wajib kurang: $v"
done
[[ $COMMIT =~ ^[0-9a-f]{40}$ ]] || die "commit harus 40 digit heksadesimal."
case "$PROFILE" in release | fast) ;; *) die "profile harus release atau fast." ;; esac
[[ -z $PATCH_SET || $PATCH_SET =~ ^[a-z0-9]{1,20}$ ]] || die "patch-set hanya boleh huruf kecil dan angka (paling banyak 20 karakter)."
if [ -n "$PATCH_SET" ]; then
  [[ $PATCH_HASH =~ ^[0-9a-f]{8}$ ]] || die "patch-hash harus 8 digit heksadesimal bila patch-set diisi."
else
  [ -z "$PATCH_HASH" ] || die "patch-hash tanpa patch-set."
fi
[ -f "$SO" ] || die "pustaka tidak ada: $SO"
[ -f "$DATA" ] || die "data mesin tidak ada: $DATA"
for f in LICENSE THIRD_PARTY_NOTICES.md; do
  [ -f "$LICENSE_DIR/$f" ] || die "berkas lisensi tidak ada: $LICENSE_DIR/$f"
done
command -v zip > /dev/null 2>&1 || die "perintah zip tidak ditemukan."
command -v unzip > /dev/null 2>&1 || die "perintah unzip tidak ditemukan."

sha256_of() {
  if command -v sha256sum > /dev/null 2>&1; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi
}
size_of() { wc -c < "$1" | tr -d '[:space:]'; }

# ELF 64-bit little-endian untuk arm64: 7f 45 4c 46, kelas 02, data 01, e_machine (byte 18-19) = b7 00.
HEADER="$(od -An -v -tx1 -N20 "$SO" | tr -d ' \n')"
if [ "${HEADER:0:10}" != "7f454c4602" ] || [ "${HEADER:10:2}" != "01" ] || [ "${HEADER:36:4}" != "b700" ]; then
  die "$SO bukan pustaka ELF 64-bit arm64 (AArch64)."
fi
unzip -tq "$DATA" > /dev/null 2>&1 || die "data mesin bukan zip yang utuh: $DATA"

# Build ID membantu mencocokkan laporan crash dengan simbol; bila alatnya tidak ada, dikosongkan.
BUILD_ID=""
if [ -n "$READELF" ] && [ -x "$(command -v "$READELF" 2> /dev/null || true)" ]; then
  BUILD_ID="$("$READELF" --notes "$SO" 2> /dev/null | sed -n 's/.*Build ID: *\([0-9a-f]*\).*/\1/p' | head -n 1 || true)"
fi

mkdir -p "$OUT"
OUT="$(cd "$OUT" && pwd)"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

cp "$SO" "$STAGE/libhorizon_android.so"
cp "$DATA" "$STAGE/engine-data.zip"
cp "$LICENSE_DIR/LICENSE" "$LICENSE_DIR/THIRD_PARTY_NOTICES.md" "$STAGE/"

{
  echo "schema=1"
  echo "abi=arm64-v8a"
  echo "source_repo=$REPO"
  echo "source_commit=$COMMIT"
  echo "profile=$PROFILE"
  echo "patch_set=$PATCH_SET"
  echo "patch_hash=$PATCH_HASH"
  echo "rust=$RUST"
  echo "ndk=$NDK"
  echo "build_id=$BUILD_ID"
  echo "built_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "so_bytes=$(size_of "$STAGE/libhorizon_android.so")"
  echo "so_sha256=$(sha256_of "$STAGE/libhorizon_android.so")"
  echo "data_bytes=$(size_of "$STAGE/engine-data.zip")"
  echo "data_sha256=$(sha256_of "$STAGE/engine-data.zip")"
} > "$STAGE/engine.properties"

rm -f "$OUT/$BUNDLE_NAME" "$OUT/$BUNDLE_NAME.sha256"
# -X: tanpa atribut tambahan; urutan tetap supaya hasilnya mudah dibandingkan.
(cd "$STAGE" && zip -q -X "$OUT/$BUNDLE_NAME" engine.properties libhorizon_android.so engine-data.zip LICENSE THIRD_PARTY_NOTICES.md)
unzip -tq "$OUT/$BUNDLE_NAME" > /dev/null 2>&1 || die "paket yang baru dibuat tidak lolos pemeriksaan zip."
echo "$(sha256_of "$OUT/$BUNDLE_NAME")  $BUNDLE_NAME" > "$OUT/$BUNDLE_NAME.sha256"

echo "Paket: $OUT/$BUNDLE_NAME ($(size_of "$OUT/$BUNDLE_NAME") byte)"
echo "SHA-256 paket: $(cut -d' ' -f1 "$OUT/$BUNDLE_NAME.sha256")"
