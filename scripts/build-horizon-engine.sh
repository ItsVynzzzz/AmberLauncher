#!/usr/bin/env bash
# Mengompilasi mesin Rust (Horizon) dari SUMBER untuk Android arm64, lalu (opsional) menerbitkannya sebagai
# Release di repo ini. Dijalankan oleh workflow "Bangun mesin Rust (Horizon)" di GitHub Actions (runner Linux
# x86-64), sekali setiap patokan commit diganti; build APK biasa hanya mengambil hasilnya
# (scripts/fetch-horizon-engine.sh), jadi tidak menunggu kompilasi Rust.
#
# Sumbernya proyek Horizon Android (MIT) pada commit yang dipatok di horizon-engine.properties. Yang dipakai
# adalah jalur build resmi proyeknya sendiri (tools/android_prepare.py dan tools/android_build.py dengan --public),
# berhenti sebelum Gradle: yang dibutuhkan hanya pustaka native dan tabel datanya.
#
# Tahap: sumber, alat, data (menyiapkan tabel data dari berkas Mojang yang diunduh sementara), kompilasi,
# kemas, terbitkan. Ringkasan waktu dan ukuran ditulis ke ringkasan run; bila gagal, ringkasan memuat tahap
# yang gagal dan ujung lognya supaya bisa disalin.
#
# Konfigurasi dari horizon-engine.properties (variabel lingkungan HORIZON_ENGINE_* menang atasnya):
#   source_repo   alamat git sumber mesin (https://)          HORIZON_ENGINE_SOURCE_REPO
#   commit        commit sumber yang dipatok (40 digit)         HORIZON_ENGINE_COMMIT
#   profile       release (ThinLTO, lambat) atau fast           HORIZON_ENGINE_PROFILE
#   patch_set     perubahan Amber pada sumber (kosong = tanpa)   HORIZON_ENGINE_PATCH_SET
#   ndk           versi NDK Android                             (bawaan 28.2.13676358)
# Lainnya: HORIZON_ENGINE_WORK (folder kerja; bawaan $RUNNER_TEMP/horizon-engine), HORIZON_ENGINE_JDK (JDK 25
# bila bukan JAVA_HOME), HORIZON_ENGINE_JOBS (batas proses paralel cargo; kecilkan bila kehabisan RAM),
# HORIZON_ENGINE_FORCE=true (sama dengan --force).
#
# Tanda Release: horizon-engine-<12 digit commit>[-<patch_set>-<hash isi patch>][-fast]. Hash 8 digit itu dihitung
# dari nama dan isi berkas third_party/horizon-patches/<patch_set>/*.diff, jadi mengedit patch Amber otomatis
# berarti mesin baru (tidak perlu mengganti nama patch_set). Build APK memakai tanda yang sama untuk memutuskan
# apakah mesin perlu dibangun (lihat --print-tag).
#
# Pemakaian:
#   scripts/build-horizon-engine.sh [--publish] [--force] [--src FOLDER] [--work FOLDER]
#   scripts/build-horizon-engine.sh --print-tag
#     --publish  terbitkan hasilnya sebagai Release (butuh gh dan GH_TOKEN dengan hak menulis konten)
#     --force    bangun dan terbitkan ulang walau Release untuk commit ini sudah ada
#     --src      pakai salinan sumber yang sudah ada (HEAD-nya harus tepat commit yang dipatok)
#     --work     folder kerja (disarankan di disk yang lapang: sumber, unduhan, dan target Rust beberapa GB)
#     --print-tag  hanya cetak tanda Release untuk konfigurasi sekarang lalu keluar (tanpa membangun apa pun)

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONFIG="$ROOT/horizon-engine.properties"
BUNDLE_NAME="horizon-engine-arm64-v8a.zip"

prop() {
  [ -f "$CONFIG" ] || return 0
  { tr -d '\r' < "$CONFIG" | grep -E "^[[:space:]]*$1[[:space:]]*=" || true; } \
    | tail -n 1 | sed -E 's/^[^=]*=[[:space:]]*//; s/[[:space:]]+$//'
}

die() { echo "build-horizon-engine: $*" >&2; exit 1; }

PUBLISH=0
FORCE=0
PRINT_TAG=0
[ "${HORIZON_ENGINE_FORCE:-}" = "true" ] && FORCE=1
SRC_ARG=""
WORK="${HORIZON_ENGINE_WORK:-}"
while [ $# -gt 0 ]; do
  case "$1" in
    --publish) PUBLISH=1; shift ;;
    --force) FORCE=1; shift ;;
    --print-tag) PRINT_TAG=1; shift ;;
    --src | --work)
      [ $# -ge 2 ] || { echo "$1 butuh nilai." >&2; exit 1; }
      if [ "$1" = "--src" ]; then SRC_ARG="$2"; else WORK="$2"; fi
      shift 2
      ;;
    -h | --help)
      awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"
      exit 0
      ;;
    *) echo "Argumen tidak dikenal: $1" >&2; exit 1 ;;
  esac
done

REPO="${HORIZON_ENGINE_SOURCE_REPO:-$(prop source_repo)}"
COMMIT="${HORIZON_ENGINE_COMMIT:-$(prop commit)}"
COMMIT="$(printf '%s' "$COMMIT" | tr 'A-F' 'a-f' | tr -d '[:space:]')"
PROFILE="${HORIZON_ENGINE_PROFILE:-$(prop profile)}"
PROFILE="${PROFILE:-release}"
PATCH_SET="${HORIZON_ENGINE_PATCH_SET-$(prop patch_set)}"
PATCH_SET="$(printf '%s' "$PATCH_SET" | tr 'A-Z' 'a-z' | tr -d '[:space:]')"
NDK_VERSION="$(prop ndk)"
NDK_VERSION="${NDK_VERSION:-28.2.13676358}"

# 8 digit pertama hash isi perubahan Amber di folder $1 (nama dan isi tiap .diff, urut nama). HARUS sama dengan
# patch_hash() di scripts/fetch-horizon-engine.sh.
patch_hash() {
  (
    cd "$1"
    export LC_ALL=C
    shopt -s nullglob
    for f in *.diff; do
      printf '%s\n' "$f"
      cat "$f"
    done
  ) | sha256sum | cut -c1-8
}

# Mengisi PATCH_DIR, PATCH_HASH, dan TAG dari konfigurasi sekarang.
engine_tag() {
  PATCH_DIR="" PATCH_HASH=""
  if [ -n "$PATCH_SET" ]; then
    PATCH_DIR="$ROOT/third_party/horizon-patches/$PATCH_SET"
    ls "$PATCH_DIR"/*.diff > /dev/null 2>&1 \
      || die "patch_set=$PATCH_SET tidak punya berkas .diff di third_party/horizon-patches/$PATCH_SET/."
    PATCH_HASH="$(patch_hash "$PATCH_DIR")"
  fi
  TAG="horizon-engine-${COMMIT:0:12}${PATCH_SET:+-$PATCH_SET-$PATCH_HASH}"
  [ "$PROFILE" = "release" ] || TAG="$TAG-$PROFILE"
}

# --print-tag: hanya cetak tanda Release, tanpa membuat folder, mengunduh, atau membangun apa pun.
if [ "$PRINT_TAG" = 1 ]; then
  [[ $COMMIT =~ ^[0-9a-f]{40}$ ]] || die "commit harus 40 digit heksadesimal (isi commit= di horizon-engine.properties)."
  case "$PROFILE" in release | fast) ;; *) die "profile harus release atau fast." ;; esac
  [[ -z $PATCH_SET || $PATCH_SET =~ ^[a-z0-9]{1,20}$ ]] || die "patch_set hanya boleh huruf kecil dan angka (paling banyak 20 karakter)."
  engine_tag
  echo "$TAG"
  exit 0
fi

[ -n "$WORK" ] || WORK="${RUNNER_TEMP:-/tmp}/horizon-engine"
mkdir -p "$WORK"
WORK="$(cd "$WORK" && pwd)"
LOGDIR="$WORK/logs"
OUT="$WORK/out"
rm -rf "$LOGDIR" "$OUT"
mkdir -p "$LOGDIR" "$OUT"

# ---- Pencatat tahap dan ringkasan ----
PHASE="persiapan"
PHASE_SLUG="00-persiapan"
PHASE_START=$SECONDS
PHASE_LOG="$LOGDIR/$PHASE_SLUG.log"
: > "$PHASE_LOG"
TIMES=()
IN_GROUP=0
STARTED=$SECONDS

# Selalu menghabiskan masukannya (tanpa GitHub Actions dibuang), supaya pipa di depannya tidak kena SIGPIPE.
summary() { if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then cat >> "$GITHUB_STEP_SUMMARY"; else cat > /dev/null; fi; }

end_phase() {
  TIMES+=("$PHASE|$((SECONDS - PHASE_START))")
  if [ "$IN_GROUP" = 1 ] && [ -n "${GITHUB_ACTIONS:-}" ]; then echo "::endgroup::"; fi
  IN_GROUP=0
}

# Memulai tahap baru: $1 nomor-nama (untuk berkas log), $2 judul.
phase() {
  end_phase
  PHASE_SLUG="$1"
  PHASE="$2"
  PHASE_START=$SECONDS
  PHASE_LOG="$LOGDIR/$PHASE_SLUG.log"
  : > "$PHASE_LOG"
  [ -z "${GITHUB_ACTIONS:-}" ] || echo "::group::$PHASE"
  IN_GROUP=1
  echo "== $PHASE =="
}

# Menjalankan perintah dan mencatat keluarannya ke log tahap ini (juga tampil di layar).
logged() {
  "$@" 2>&1 | tee -a "$PHASE_LOG"
}

fmt_time() { printf '%dm%02ds' $(($1 / 60)) $(($1 % 60)); }

on_exit() {
  local rc=$?
  [ "$rc" -ne 0 ] || return 0
  [ "$IN_GROUP" = 1 ] && [ -n "${GITHUB_ACTIONS:-}" ] && echo "::endgroup::"
  local tail_text errors
  tail_text="$(sed -E 's/\x1b\[[0-9;]*[A-Za-z]//g' "$PHASE_LOG" 2> /dev/null | tail -n 60 || true)"
  errors="$(sed -E 's/\x1b\[[0-9;]*[A-Za-z]//g' "$PHASE_LOG" 2> /dev/null | grep -E '^(error|Error|ERROR)|panicked|No such file|not found' | head -n 15 || true)"
  echo "::error title=Mesin Rust (Horizon) gagal dibangun::Berhenti di tahap: $PHASE (kode $rc). Ujung lognya ada di ringkasan run."
  {
    echo "### Mesin Rust (Horizon) GAGAL dibangun"
    echo
    echo "- Tahap: **$PHASE** (kode keluar $rc), setelah $(fmt_time $((SECONDS - STARTED)))"
    echo "- Sumber: \`${REPO:-?}\` commit \`${COMMIT:0:12}\` ($PROFILE)${PATCH_SET:+, perubahan Amber: $PATCH_SET}"
    if [ -n "$errors" ]; then
      echo
      echo "Baris galat:"
      echo
      echo '```'
      printf '%s\n' "$errors"
      echo '```'
    fi
    echo
    echo "Ujung log tahap ini (60 baris terakhir):"
    echo
    echo '```'
    printf '%s\n' "$tail_text"
    echo '```'
  } | summary
}
trap on_exit EXIT


# ---- Pemeriksaan konfigurasi ----
[ -n "$REPO" ] || die "source_repo belum diisi di horizon-engine.properties."
case "$REPO" in https://*) ;; *) die "source_repo harus diawali https://" ;; esac
[[ $COMMIT =~ ^[0-9a-f]{40}$ ]] || die "commit harus 40 digit heksadesimal (isi commit= di horizon-engine.properties)."
case "$PROFILE" in release | fast) ;; *) die "profile harus release atau fast." ;; esac
[[ -z $PATCH_SET || $PATCH_SET =~ ^[a-z0-9]{1,20}$ ]] || die "patch_set hanya boleh huruf kecil dan angka (paling banyak 20 karakter)."
[ "$(uname -s)" = "Linux" ] && [ "$(uname -m)" = "x86_64" ] || die "skrip ini hanya untuk Linux x86-64 (runner GitHub Actions)."

engine_tag
echo "Sumber: $REPO @ $COMMIT ($PROFILE), perubahan Amber: ${PATCH_SET:-tanpa}${PATCH_HASH:+ ($PATCH_HASH)}, NDK $NDK_VERSION, tag Release: $TAG"

if [ "$PUBLISH" = 1 ]; then
  command -v gh > /dev/null 2>&1 || die "--publish butuh perintah gh."
  [ -n "${GH_TOKEN:-${GITHUB_TOKEN:-}}" ] || die "--publish butuh GH_TOKEN."
  if gh release view "$TAG" > /dev/null 2>&1; then
    if [ "$FORCE" = 1 ]; then
      echo "Release $TAG sudah ada, akan diganti (--force)."
    else
      echo "Release $TAG sudah ada: tidak perlu membangun lagi (pakai force untuk membangun ulang)."
      {
        echo "### Mesin Rust (Horizon): sudah ada"
        echo
        echo "Release \`$TAG\` sudah berisi mesin untuk commit \`${COMMIT:0:12}\` ($PROFILE${PATCH_SET:+, perubahan Amber: $PATCH_SET}). Tidak ada yang dibangun. Pilih *force* untuk membangun ulang."
      } | summary
      PHASE="selesai"
      exit 0
    fi
  fi
fi

# ---- 1. Sumber ----
phase 01-sumber "1/6 Ambil sumber (commit dipatok)"
if [ -n "$SRC_ARG" ]; then
  SRC="$(cd "$SRC_ARG" && pwd)"
else
  SRC="$WORK/src"
  rm -rf "$SRC"
  git init -q "$SRC"
  logged git -C "$SRC" fetch -q --depth 1 "$REPO" "$COMMIT"
  git -C "$SRC" checkout -q --detach FETCH_HEAD
fi
[ "$(git -C "$SRC" rev-parse HEAD)" = "$COMMIT" ] || die "HEAD sumber bukan commit yang dipatok ($COMMIT)."
for f in tools/android_prepare.py tools/android_build.py research-lock.json rust-toolchain.toml LICENSE THIRD_PARTY_NOTICES.md; do
  [ -f "$SRC/$f" ] || die "sumber tidak berisi $f: bentuk repo sumber berubah, patokan perlu ditinjau."
done

# Perubahan kecil milik AmberLauncher (third_party/horizon-patches/<patch_set>/*.diff), diterapkan berurutan nama.
# Salinan --src yang sudah pernah diberi patch dikenali dan dilewati. Tidak ada commit baru dibuat.
if [ -n "$PATCH_SET" ]; then
  PATCH_DIR="$ROOT/third_party/horizon-patches/$PATCH_SET"
  shopt -s nullglob
  PATCH_FILES=("$PATCH_DIR"/*.diff)
  shopt -u nullglob
  [ "${#PATCH_FILES[@]}" -gt 0 ] || die "patch_set=$PATCH_SET tidak punya berkas .diff di third_party/horizon-patches/$PATCH_SET/."
  for p in "${PATCH_FILES[@]}"; do
    if git -C "$SRC" apply --check "$p" > /dev/null 2>&1; then
      logged git -C "$SRC" apply "$p"
      echo "Perubahan Amber diterapkan: ${p##*/}"
    elif git -C "$SRC" apply --reverse --check "$p" > /dev/null 2>&1; then
      echo "Perubahan Amber sudah terpasang di sumber ini: ${p##*/}"
    else
      git -C "$SRC" apply --check "$p" 2>&1 | tee -a "$PHASE_LOG" || true
      die "${p##*/} tidak cocok dengan sumber pada commit yang dipatok. Buat ulang berkasnya dari sumber di commit itu."
    fi
  done
fi

# ---- 2. Alat ----
phase 02-alat "2/6 Siapkan alat (Python, JDK, NDK, Rust)"
python3 -c 'import sys; sys.exit(0 if sys.version_info >= (3, 11) else 1)' || die "butuh Python 3.11 atau lebih baru."

JDK="${HORIZON_ENGINE_JDK:-${JAVA_HOME:-}}"
[ -n "$JDK" ] && [ -x "$JDK/bin/javac" ] || die "JDK dengan javac tidak ditemukan (isi JAVA_HOME atau HORIZON_ENGINE_JDK ke JDK 25)."
JAVA_NEED="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["minecraft"]["java_major"])' "$SRC/research-lock.json")"
JAVA_HAVE="$("$JDK/bin/javac" -version 2>&1 | sed -n 's/^javac \([0-9]*\).*/\1/p' | head -n 1)"
[ -n "$JAVA_HAVE" ] && [ "$JAVA_HAVE" -ge "$JAVA_NEED" ] || die "butuh JDK $JAVA_NEED atau lebih baru untuk eksporter tabel data; yang ada: javac ${JAVA_HAVE:-?} di $JDK."
echo "JDK: javac $JAVA_HAVE ($JDK), dibutuhkan >= $JAVA_NEED"

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
NDK_HOME="$SDK_ROOT/ndk/$NDK_VERSION"
if [ ! -d "$NDK_HOME" ]; then
  echo "Memasang NDK $NDK_VERSION ..."
  # "yes" selalu berakhir dengan kode 141 (SIGPIPE) begitu sdkmanager selesai; hasil sebenarnya dicek di bawah.
  yes | "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" "ndk;$NDK_VERSION" > /dev/null || true
fi
NDK_BIN="$NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
[ -x "$NDK_BIN/clang" ] || die "NDK $NDK_VERSION tidak ditemukan di $SDK_ROOT/ndk."
echo "NDK: $NDK_HOME"

# Alat build proyek sumber memilih NDK dan build-tools "terbaru" di folder SDK. Supaya yang dipakai pasti NDK yang
# dipatok (bukan NDK lain di runner), diberikan SDK mini berisi tepat satu NDK. Untuk kompilasi saja folder
# build-tools cukup ada dan tidak dipakai.
SDK_MINI="$WORK/sdk"
rm -rf "$SDK_MINI"
mkdir -p "$SDK_MINI/ndk" "$SDK_MINI/build-tools/35.0.0"
ln -s "$NDK_HOME" "$SDK_MINI/ndk/$NDK_VERSION"

RUST_CHANNEL="$(sed -n 's/^channel *= *"\([^"]*\)".*/\1/p' "$SRC/rust-toolchain.toml" | head -n 1)"
[ -n "$RUST_CHANNEL" ] || die "rust-toolchain.toml sumber tidak menyebut channel."
command -v rustup > /dev/null 2>&1 || die "rustup tidak ditemukan."
logged rustup toolchain install "$RUST_CHANNEL" --profile minimal --component clippy --component rustfmt --no-self-update
logged rustup target add --toolchain "$RUST_CHANNEL" aarch64-linux-android
export RUSTUP_TOOLCHAIN="$RUST_CHANNEL"
echo "Rust: $(rustc --version)"

# ---- 3. Data ----
phase 03-data "3/6 Siapkan tabel data (unduh berkas Mojang sementara, jalankan eksporter)"
MC_VERSION="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["minecraft"]["version"])' "$SRC/research-lock.json")"
RESOURCES="$SRC/.local/horizon-client/$MC_VERSION"
if [ -f "$RESOURCES/horizon-resources.json" ] && [ -f "$RESOURCES/worldgen/blocks/structure-blocks.json" ]; then
  echo "Tabel data untuk Minecraft $MC_VERSION sudah ada, dipakai ulang."
else
  # Lewat scripts/prepare-horizon-data.py: memakai berkas versi Mojang yang dipatok proyek sumber, bukan yang
  # kebetulan terbit ulang hari ini (horizon-prepare menolak yang tidak dikenalnya).
  (cd "$SRC" && logged env HORIZON_TELEMETRY_URL=off python3 "$ROOT/scripts/prepare-horizon-data.py" "$SRC" --java-home "$JDK")
fi

# ---- 4. Kompilasi ----
phase 04-kompilasi "4/6 Kompilasi mesin Rust (arm64, profil $PROFILE)"
TARGET_DIR="$WORK/target"
[ -z "${HORIZON_ENGINE_JOBS:-}" ] || export CARGO_BUILD_JOBS="$HORIZON_ENGINE_JOBS"
(cd "$SRC" && logged env CARGO_TARGET_DIR="$TARGET_DIR" HORIZON_TELEMETRY_URL=off python3 tools/android_build.py \
  --abi arm64-v8a --public --rust-only --profile "$PROFILE" --sdk "$SDK_MINI" --java-home "$JDK")
LIB="$TARGET_DIR/aarch64-linux-android/$PROFILE/libhorizon_android.so"
[ -f "$LIB" ] || die "kompilasi selesai tetapi pustaka tidak ada: $LIB"
echo "Pustaka mentah: $(wc -c < "$LIB" | tr -d ' ') byte"

# ---- 5. Kemas ----
phase 05-kemas "5/6 Strip pustaka, susun tabel data, kemas"
STAGE="$WORK/stage"
rm -rf "$STAGE"
mkdir -p "$STAGE"
cp "$LIB" "$STAGE/libhorizon_android.so"
# Sama seperti jalur APK proyek sumber: llvm-strip --strip-unneeded.
logged "$NDK_BIN/llvm-strip" --strip-unneeded "$STAGE/libhorizon_android.so"
# Tabel data dibuat oleh fungsi proyek sumber sendiri; hanya berkas hasil generate yang diizinkan untuk build
# publik (daftar putih generated_engine_file di tools/android_build.py).
if ! DATA_ZIP="$(cd "$SRC" && python3 - "$SRC" 2> "$STAGE/python.err" <<'PY'
import sys
sys.path.insert(0, sys.argv[1] + '/tools')
import android_build
print(android_build.engine_data(True))
PY
)"; then
  tee -a "$PHASE_LOG" < "$STAGE/python.err" >&2
  die "gagal menyusun tabel data."
fi
DATA_ZIP="$(printf '%s\n' "$DATA_ZIP" | tail -n 1)"
[ -f "$DATA_ZIP" ] || die "tabel data tidak terbentuk: $DATA_ZIP"
logged bash "$ROOT/scripts/package-horizon-engine.sh" \
  --so "$STAGE/libhorizon_android.so" --data "$DATA_ZIP" --license-dir "$SRC" --out "$OUT" \
  --repo "$REPO" --commit "$COMMIT" --profile "$PROFILE" --patch-set "$PATCH_SET" --patch-hash "$PATCH_HASH" --rust "$(rustc --version)" --ndk "$NDK_VERSION" \
  --readelf "$NDK_BIN/llvm-readelf"

# ---- 6. Terbitkan ----
if [ "$PUBLISH" = 1 ]; then
  phase 06-terbit "6/6 Terbitkan Release $TAG"
  if gh release view "$TAG" > /dev/null 2>&1; then
    logged gh release delete "$TAG" --cleanup-tag --yes
  fi
  NOTES="$WORK/release-notes.md"
  {
    echo "Mesin Rust (Horizon) untuk Android arm64, dikompilasi dari sumber oleh workflow di repo ini."
    echo
    echo "- Sumber: $REPO"
    echo "- Commit: \`$COMMIT\`"
    [ -z "$PATCH_SET" ] || echo "- Perubahan Amber pada sumber: \`$PATCH_SET\`, isi \`$PATCH_HASH\` (third_party/horizon-patches/$PATCH_SET)"
    echo "- Profil: $PROFILE, NDK $NDK_VERSION, $(rustc --version)"
    echo
    echo "**Ini bukan rilis aplikasi.** Berkas di sini dipasang otomatis ke APK oleh build GitHub Actions"
    echo "(scripts/fetch-horizon-engine.sh); tidak perlu diunduh atau dipasang sendiri."
  } > "$NOTES"
  TARGET_SHA="${GITHUB_SHA:-$(git -C "$ROOT" rev-parse HEAD)}"
  # Pra-rilis dengan tag bukan nomor versi: tidak dianggap pembaruan aplikasi dan tidak mengubah penomoran versi.
  logged gh release create "$TAG" "$OUT/$BUNDLE_NAME" "$OUT/$BUNDLE_NAME.sha256" \
    --prerelease --target "$TARGET_SHA" --title "Mesin Rust (Horizon) ${COMMIT:0:12}${PATCH_SET:+ + $PATCH_SET} ($PROFILE)" --notes-file "$NOTES"
else
  echo "Tanpa --publish: paket ada di $OUT"
fi
end_phase
PHASE="selesai"

# ---- Ringkasan ----
M="$(mktemp)"
unzip -p "$OUT/$BUNDLE_NAME" engine.properties > "$M"
val() { { grep -E "^$1=" "$M" || true; } | tail -n 1 | sed -E 's/^[^=]*=//'; }
{
  echo "### Mesin Rust (Horizon) berhasil dibangun"
  echo
  echo "- Sumber: \`$REPO\` commit \`${COMMIT:0:12}\` ($PROFILE)${PATCH_SET:+, perubahan Amber: $PATCH_SET}"
  echo "- Alat: $(val rust), NDK $NDK_VERSION, javac $JAVA_HAVE"
  echo "- \`libhorizon_android.so\` (di-strip): $(val so_bytes) byte, sha256 \`$(val so_sha256)\`"
  echo "- \`engine-data.zip\`: $(val data_bytes) byte"
  echo "- Paket: $(wc -c < "$OUT/$BUNDLE_NAME" | tr -d ' ') byte, sha256 \`$(cut -d' ' -f1 "$OUT/$BUNDLE_NAME.sha256")\`"
  [ "$PUBLISH" = 1 ] && echo "- Release: \`$TAG\` (pra-rilis). Build APK berikutnya memasangnya otomatis." || true
  echo
  echo "| Tahap | Waktu |"
  echo "|---|---|"
  for t in "${TIMES[@]}"; do
    echo "| ${t%%|*} | $(fmt_time "${t##*|}") |"
  done
  echo
  echo "Total: $(fmt_time $((SECONDS - STARTED)))"
} | tee /dev/stderr | summary
rm -f "$M"
