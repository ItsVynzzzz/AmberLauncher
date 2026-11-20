#!/usr/bin/env bash
# Mengompilasi mesin Rust (Horizon) dari SUMBER untuk Android arm64 (dan armeabi-v7a, eksperimental), lalu
# (opsional) menerbitkannya sebagai Release di repo ini. Dijalankan oleh workflow "Bangun mesin Rust (Horizon)" di GitHub Actions (runner Linux
# x86-64), sekali setiap patokan commit diganti; build APK biasa hanya mengambil hasilnya
# (scripts/fetch-horizon-engine.sh), jadi tidak menunggu kompilasi Rust.
#
# Sumbernya proyek Horizon/Starling (MIT) pada commit yang dipatok di horizon-engine.properties. Dua bentuk sumber dikenali
# otomatis (LAYOUT):
#   horizon   Horizon Android 0.7.x: jalur build resmi tools/android_prepare.py dan tools/android_build.py (--public),
#             berhenti sebelum Gradle: yang dibutuhkan pustaka native dan tabel datanya.
#   starling  Starling 0.8+ (mis. CihuyAkz/starling-extras): tools/android_*.py sudah tidak ada. Pustaka dibangun dengan
#             perintah yang sama seperti packaging/android/build_apk.py (cargo ndk -p horizon-android), ditambah
#             peluncur java/javac untuk menyiapkan game di HP, aset APK, serta kelas Java dan resource-nya (satu commit dengan
#             pustaka). Tabel data TIDAK dibangun di CI: game disiapkan di HP.
#
# Tahap: sumber, alat, data (menyiapkan tabel data dari berkas Mojang yang diunduh sementara), kompilasi,
# kemas, mesin 32-bit, terbitkan. Mesin 32-bit (armeabi-v7a) dibangun SETELAH arm64 selesai dan dikemas, dan
# kegagalannya tidak menggagalkan apa pun: Release tetap terbit dengan paket arm64 saja, dan ringkasan run
# menjelaskan kenapa paket 32-bit tidak ada. Ringkasan waktu dan ukuran ditulis ke ringkasan run; bila gagal, ringkasan memuat tahap
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
# HORIZON_ENGINE_FORCE=true (sama dengan --force), HORIZON_ENGINE_ARM32=off (lewati mesin 32-bit).
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
# AMBER_ENGINE (horizon | starling) dan HORIZON_ENGINE_CONFIG dipasang oleh bagian "dua mesin" di bawah saat skrip ini
# menjalankan dirinya sendiri untuk satu mesin; dipanggil biasa, hasilnya sama seperti sebelum ada Starling.
ENGINE_NAME="${AMBER_ENGINE:-horizon}"
ENGINE_LABEL=Horizon
[ "$ENGINE_NAME" != starling ] || ENGINE_LABEL=Starling
CONFIG="${HORIZON_ENGINE_CONFIG:-$ROOT/horizon-engine.properties}"
BUNDLE_NAME="$ENGINE_NAME-engine-arm64-v8a.zip"
BUNDLE_NAME32="horizon-engine-armeabi-v7a.zip"

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


# ---- Dua mesin dalam satu run: Horizon (horizon-engine.properties) dan Starling (starling-engine.properties) ----
# Workflow hanya mengenal satu skrip dan satu tanda Release, jadi skrip ini yang membagi kerjanya. --print-tag mencetak
# tanda gabungan (Horizon + Starling); membangun menjalankan skrip ini lagi untuk tiap mesin (AMBER_ENGINE_CHILD=1)
# dengan pengaturan mesin itu saja. Satu mesin gagal tidak menggagalkan yang lain: Release diterbitkan dengan paket
# yang berhasil dan run ditandai gagal supaya kelihatan. Tanpa starling-engine.properties (atau enabled=false) skrip
# ini bekerja persis seperti sebelumnya, hanya untuk Horizon.
STARLING_CONFIG="$ROOT/starling-engine.properties"
sprop() {
  [ -f "$STARLING_CONFIG" ] || return 0
  { tr -d '\r' < "$STARLING_CONFIG" | grep -E "^[[:space:]]*$1[[:space:]]*=" || true; } \
    | tail -n 1 | sed -E 's/^[^=]*=[[:space:]]*//; s/[[:space:]]+$//'
}
if [ -z "${AMBER_ENGINE_CHILD:-}" ] && [ -f "$STARLING_CONFIG" ] && [ "$(sprop enabled)" != "false" ]; then
  child() { # $1 = horizon | starling; sisanya argumen untuk skrip ini
    local name="$1"
    shift
    if [ "$name" = starling ]; then
      env -u HORIZON_ENGINE_COMMIT -u HORIZON_ENGINE_SOURCE_REPO -u HORIZON_ENGINE_PATCH_SET -u HORIZON_ENGINE_PROFILE \
        AMBER_ENGINE_CHILD=1 AMBER_ENGINE=starling HORIZON_ENGINE_CONFIG="$STARLING_CONFIG" bash "$0" "$@"
    else
      AMBER_ENGINE_CHILD=1 AMBER_ENGINE=horizon bash "$0" "$@"
    fi
  }
  [ -z "$SRC_ARG" ] || die "--src tidak bisa dipakai bila dua mesin dibangun sekaligus (pakai enabled=false di starling-engine.properties)."
  HTAG="$(child horizon --print-tag)" || die "konfigurasi mesin Horizon tidak valid (horizon-engine.properties)."
  STAG="$(child starling --print-tag)" || die "konfigurasi mesin Starling tidak valid (starling-engine.properties)."
  TAG="$HTAG-$STAG"
  if [ "$PRINT_TAG" = 1 ]; then
    echo "$TAG"
    exit 0
  fi
  BASE="${WORK:-${RUNNER_TEMP:-/tmp}/horizon-engine}"
  mkdir -p "$BASE"
  BASE="$(cd "$BASE" && pwd)"
  if [ "$PUBLISH" = 1 ]; then
    command -v gh > /dev/null 2>&1 || die "--publish butuh perintah gh."
    [ -n "${GH_TOKEN:-${GITHUB_TOKEN:-}}" ] || die "--publish butuh GH_TOKEN."
    if gh release view "$TAG" > /dev/null 2>&1; then
      if [ "$FORCE" = 1 ]; then
        echo "Release $TAG sudah ada, akan diganti (--force)."
      else
        echo "Release $TAG sudah ada: tidak perlu membangun lagi (pakai force untuk membangun ulang)."
        if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
          printf '### Mesin Rust (Horizon dan Starling): sudah ada\n\nRelease `%s` sudah ada, tidak ada yang dibangun. Pilih *force* untuk membangun ulang.\n' "$TAG" >> "$GITHUB_STEP_SUMMARY"
        fi
        exit 0
      fi
    fi
  fi
  FORCE_ARG=()
  [ "$FORCE" = 1 ] && FORCE_ARG=(--force)
  H_OK=1
  S_OK=1
  echo "=== Mesin 1 dari 2: Horizon ==="
  child horizon ${FORCE_ARG[@]+"${FORCE_ARG[@]}"} --work "$BASE/horizon" || H_OK=0
  echo "=== Mesin 2 dari 2: Starling ==="
  child starling ${FORCE_ARG[@]+"${FORCE_ARG[@]}"} --work "$BASE/starling" || S_OK=0
  HB="$BASE/horizon/out/horizon-engine-arm64-v8a.zip"
  SB="$BASE/starling/out/starling-engine-arm64-v8a.zip"
  [ -f "$HB" ] && [ -f "$HB.sha256" ] || H_OK=0
  [ -f "$SB" ] && [ -f "$SB.sha256" ] || S_OK=0
  echo "Hasil: Horizon $([ "$H_OK" = 1 ] && echo berhasil || echo GAGAL), Starling $([ "$S_OK" = 1 ] && echo berhasil || echo GAGAL)"
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    {
      echo "### Hasil dua mesin Rust"
      echo
      echo "- Horizon: $([ "$H_OK" = 1 ] && echo berhasil || echo '**gagal** (lihat ringkasan dan log di atas)')"
      echo "- Starling: $([ "$S_OK" = 1 ] && echo berhasil || echo '**gagal** (lihat ringkasan dan log di atas)')"
      [ "$H_OK$S_OK" = 11 ] || echo "- Yang berhasil tetap diterbitkan dan dipasang. Untuk mencoba lagi yang gagal tanpa mengubah apa pun: jalankan ulang workflow dengan mesin = ulang."
    } >> "$GITHUB_STEP_SUMMARY"
  fi
  [ "$H_OK$S_OK" != 00 ] || die "kedua mesin gagal dibangun, tidak ada yang diterbitkan."
  if [ "$PUBLISH" != 1 ]; then
    echo "Tanpa --publish: paket ada di $BASE/horizon/out dan $BASE/starling/out"
    [ "$H_OK$S_OK" = 11 ]
    exit $?
  fi
  gh release view "$TAG" > /dev/null 2>&1 && gh release delete "$TAG" --cleanup-tag --yes
  ASSETS=()
  [ "$H_OK" != 1 ] || ASSETS+=("$HB" "$HB.sha256")
  if [ "$H_OK" = 1 ] && [ -f "$BASE/horizon/out/horizon-engine-armeabi-v7a.zip" ]; then
    ASSETS+=("$BASE/horizon/out/horizon-engine-armeabi-v7a.zip" "$BASE/horizon/out/horizon-engine-armeabi-v7a.zip.sha256")
  fi
  [ "$S_OK" != 1 ] || ASSETS+=("$SB" "$SB.sha256")
  NOTES="$BASE/release-notes.md"
  {
    echo "Mesin Rust Amber: Horizon dan Starling, dikompilasi dari sumber oleh workflow di repo ini."
    echo
    echo "- Horizon: $([ "$H_OK" = 1 ] && echo "ada ($HTAG)" || echo "GAGAL dibangun, tidak ada di Release ini")"
    echo "- Starling: $([ "$S_OK" = 1 ] && echo "ada ($STAG)" || echo "GAGAL dibangun, tidak ada di Release ini")"
    echo
    echo "**Ini bukan rilis aplikasi.** Berkas di sini dipasang otomatis ke APK oleh build GitHub Actions"
    echo "(scripts/fetch-horizon-engine.sh); tidak perlu diunduh atau dipasang sendiri."
  } > "$NOTES"
  TARGET_SHA="${GITHUB_SHA:-$(git -C "$ROOT" rev-parse HEAD)}"
  gh release create "$TAG" "${ASSETS[@]}" --prerelease --target "$TARGET_SHA" \
    --title "Mesin Rust: Horizon + Starling" --notes-file "$NOTES"
  [ "$H_OK$S_OK" = 11 ] || die "satu mesin gagal dibangun; yang berhasil sudah diterbitkan ($TAG)."
  exit 0
fi

REPO="${HORIZON_ENGINE_SOURCE_REPO:-$(prop source_repo)}"
COMMIT="${HORIZON_ENGINE_COMMIT:-$(prop commit)}"
COMMIT="$(printf '%s' "$COMMIT" | tr 'A-F' 'a-f' | tr -d '[:space:]')"
PROFILE="${HORIZON_ENGINE_PROFILE:-$(prop profile)}"
PROFILE="${PROFILE:-release}"
PATCH_SET="${HORIZON_ENGINE_PATCH_SET-$(prop patch_set)}"
PATCH_SET="$(printf '%s' "$PATCH_SET" | tr 'A-Z' 'a-z' | tr -d '[:space:]')"
NDK_VERSION="$(prop ndk)"
NDK_VERSION="${NDK_VERSION:-28.2.13676358}"

# Starling: mod native (id folder di native-mods/ pada sumber) yang dikompilasi ke dalam pustaka mesin oleh build ini,
# lewat horizon-mixins seperti native_build.sh milik sumber (docs/rust-engine.md). Dipisah spasi atau koma. Kosong =
# pustaka tanpa mod. Tanpa ini mod bawaan Starling tidak ada di pustakanya: build_apk.py hanya menjalankan `cargo ndk`.
NATIVE_MODS=""
if [ "$ENGINE_NAME" = starling ]; then
  NATIVE_MODS="${HORIZON_ENGINE_NATIVE_MODS-$(prop native_mods)}"
  NATIVE_MODS="$(printf '%s' "$NATIVE_MODS" | tr 'A-Z,' 'a-z ' | tr -s '[:space:]' ' ' | sed -E 's/^ +//; s/ +$//')"
  NATIVE_RE='^[a-z0-9_]{1,64}( [a-z0-9_]{1,64})*$'
  [[ -z $NATIVE_MODS || $NATIVE_MODS =~ $NATIVE_RE ]] \
    || die "native_mods hanya boleh berisi id (huruf kecil, angka, garis bawah) dipisah spasi atau koma."
  NATIVE_MODS="$(printf '%s\n' $NATIVE_MODS | sort -u | tr '\n' ' ' | sed 's/ $//')"
fi

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
  TAG="$ENGINE_NAME-engine-${COMMIT:0:12}${PATCH_SET:+-$PATCH_SET-$PATCH_HASH}"
  [ "$PROFILE" = "release" ] || TAG="$TAG-$PROFILE"
  # Nomor revisi (hanya Starling): dinaikkan bila skrip build berubah tanpa mengubah commit atau patch, supaya mesin
  # dibangun ulang otomatis.
  REVISION="$(prop revision)"
  [ -z "$REVISION" ] || TAG="$TAG-r$REVISION"
  # Daftar mod native (hanya Starling) ikut tanda: mengubahnya berarti mesin baru.
  [ -z "$NATIVE_MODS" ] || TAG="$TAG-m$(printf '%s' "$NATIVE_MODS" | sha256sum | cut -c1-6)"
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
  echo "::error title=Mesin Rust ($ENGINE_LABEL) gagal dibangun::Berhenti di tahap: $PHASE (kode $rc). Ujung lognya ada di ringkasan run."
  {
    echo "### Mesin Rust ($ENGINE_LABEL) GAGAL dibangun"
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
        echo "### Mesin Rust ($ENGINE_LABEL): sudah ada"
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
if [ -f "$SRC/tools/android_build.py" ]; then
  LAYOUT=horizon
  NEED_FILES="tools/android_prepare.py tools/android_build.py research-lock.json rust-toolchain.toml LICENSE THIRD_PARTY_NOTICES.md"
elif [ -f "$SRC/packaging/android/build_apk.py" ]; then
  LAYOUT=starling
  NEED_FILES="Cargo.toml research-lock.json crates/horizon-theme/src/lib.rs packaging/android/build_apk.py packaging/android/native/java_launcher.c LICENSE THIRD_PARTY_NOTICES.md"
else
  die "bentuk sumber tidak dikenal: tidak ada tools/android_build.py maupun packaging/android/build_apk.py. Patokan perlu ditinjau."
fi
for f in $NEED_FILES; do
  [ -f "$SRC/$f" ] || die "sumber ($LAYOUT) tidak berisi $f: bentuk repo sumber berubah, patokan perlu ditinjau."
done
echo "Bentuk sumber: $LAYOUT"

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
# Starling: launcher mengunduh berkas resmi Minecraft sendiri (StarlingFiles.kt) dengan patokan salinan di
# assets/starling-pins.json. Salinan itu harus sama dengan research-lock.json sumber yang dibangun; kalau beda
# (commit= di starling-engine.properties dinaikkan tanpa memperbarui salinan), launcher akan menolak atau salah
# mengunduh, jadi build dihentikan di sini.
if [ "$ENGINE_NAME" = "starling" ]; then
  python3 - "$SRC/research-lock.json" "$ROOT/app/src/main/assets/starling-pins.json" <<'PY' || die "app/src/main/assets/starling-pins.json tidak sama dengan blok minecraft di research-lock.json sumber Starling. Salin ulang version, manifest_sha1, accepted_manifest_sha1, client_sha1, dan server_sha1 dari sumber."
import json, sys
lock = json.load(open(sys.argv[1]))["minecraft"]
pins = json.load(open(sys.argv[2]))
bad = [k for k in ("version", "manifest_sha1", "accepted_manifest_sha1", "client_sha1", "server_sha1")
       if lock.get(k) != pins.get(k)]
if bad:
    print("berbeda: " + ", ".join(bad), file=sys.stderr)
    sys.exit(1)
PY
  echo "Patokan berkas Starling: starling-pins.json cocok dengan research-lock.json"
fi
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

if [ -f "$SRC/rust-toolchain.toml" ]; then
  RUST_CHANNEL="$(sed -n 's/^channel *= *"\([^"]*\)".*/\1/p' "$SRC/rust-toolchain.toml" | head -n 1)"
else
  # Starling tidak mematok toolchain; CI proyeknya memakai stable terbaru (rust-version 1.85 ke atas).
  RUST_CHANNEL=stable
fi
[ -n "$RUST_CHANNEL" ] || die "rust-toolchain.toml sumber tidak menyebut channel."
command -v rustup > /dev/null 2>&1 || die "rustup tidak ditemukan."
logged rustup toolchain install "$RUST_CHANNEL" --profile minimal --component clippy --component rustfmt --no-self-update
logged rustup target add --toolchain "$RUST_CHANNEL" aarch64-linux-android
export RUSTUP_TOOLCHAIN="$RUST_CHANNEL"
echo "Rust: $(rustc --version)"
if [ "$LAYOUT" = starling ]; then
  # Versi yang sama dengan CI proyek sumber (.github/workflows/android.yml).
  if ! cargo ndk --version > /dev/null 2>&1; then
    logged cargo install cargo-ndk --version 3.5.4 --locked
  fi
  echo "cargo-ndk: $(cargo ndk --version)"
fi

# ---- 2b. Mod native (hanya Starling) ----
# Sama dengan packaging/android/native_build.sh milik sumber, yang dipakai CI-nya untuk APK Dev "dengan mod bawaan":
# bundel di native-mods/<id>/mixins/<id> disisipkan ke kode game oleh horizon-mixins, lalu pustaka dikompilasi dari
# hasilnya. Bedanya, sumbernya adalah sumber yang SUDAH diberi perubahan Amber (native_build.sh membaca commit bersih),
# dan hasilnya tidak menjadi APK melainkan pustaka yang dikemas seperti biasa.
TARGET_DIR="$WORK/target"
[ -z "${HORIZON_ENGINE_JOBS:-}" ] || export CARGO_BUILD_JOBS="$HORIZON_ENGINE_JOBS"
MIN_SDK=26
if [ "$LAYOUT" = starling ]; then
  MIN_SDK="$(sed -n 's/^MIN_SDK *= *\([0-9][0-9]*\).*/\1/p' "$SRC/packaging/android/build_apk.py" | head -n 1)"
  MIN_SDK="${MIN_SDK:-26}"
fi
LIB="$TARGET_DIR/aarch64-linux-android/$PROFILE/libhorizon_android.so"

# Kompilasi pustaka Starling dari folder sumber $1: perintah dan flag yang sama dengan build_library() di
# packaging/android/build_apk.py (halaman 16 KB dan BuildId di pustaka). AMBER_EMBEDDED dibaca patch amber5 saat
# kompilasi (native_builds.rs): tanpa tombol Compile ke server pihak ketiga di mesin milik Amber.
compile_starling() {
  (cd "$1" && logged env CARGO_TARGET_DIR="$TARGET_DIR" HORIZON_TELEMETRY_URL=off \
    ANDROID_NDK_HOME="$NDK_HOME" ANDROID_HOME="$SDK_ROOT" \
    HORIZON_ANDROID_BUILD_COMMIT="$COMMIT" HORIZON_ANDROID_BUILD_DIRTY=false \
    AMBER_EMBEDDED=1 \
    RUSTFLAGS="${RUSTFLAGS:-} -C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,--build-id=sha1" \
    cargo ndk -t arm64-v8a --platform "$MIN_SDK" build --profile "$PROFILE" -p horizon-android)
}

# Menyusun mod native ke salinan sumber lalu mengompilasinya. Dipanggil di dalam `if`, jadi `set -e` tidak berlaku di
# sini: tiap langkah yang bisa gagal diakhiri `|| return 1`. Gagal = pemanggil membangun tanpa mod (tidak fatal).
native_mods_build() {
  local rev git_url work stage mods tools id bundle manifest lib_line lib_attr
  rev="$(sed -n 's/^MIXINS_REV=\([0-9a-f]\{40\}\).*/\1/p' "$SRC/packaging/android/native_build.sh" | head -n 1)"
  git_url="$(sed -n 's|^MIXINS_GIT=\(https://[^ ]*\).*|\1|p' "$SRC/packaging/android/native_build.sh" | head -n 1)"
  [[ $rev =~ ^[0-9a-f]{40}$ && -n $git_url ]] \
    || { echo "native_mods: MIXINS_REV atau MIXINS_GIT tidak terbaca dari packaging/android/native_build.sh."; return 1; }
  [ -f "$SRC/packaging/android/native_mod_adapt.py" ] \
    || { echo "native_mods: packaging/android/native_mod_adapt.py tidak ada di sumber."; return 1; }
  tools="$WORK/mixins-tools"
  work="$WORK/native-src"
  stage="$WORK/native-stage"
  mods="$WORK/native-mods"
  rm -rf "$work" "$stage" "$mods"
  mkdir -p "$work" "$mods"

  if [ ! -x "$tools/bin/horizon-mixins" ]; then
    logged cargo install --locked --git "$git_url" --rev "$rev" --root "$tools" horizon-mixins-cli || return 1
  fi
  [ -x "$tools/bin/horizon-mixins" ] || { echo "native_mods: horizon-mixins tidak terpasang."; return 1; }

  # Sumber (dengan perubahan Amber) tanpa .git dan tanpa tautan: horizon-mixins menolak tautan.
  (cd "$SRC" && tar --exclude=./.git --exclude=./target -cf - .) | tar -xf - -C "$work" || return 1
  find "$work" -type l -delete
  printf '%s\n' "$COMMIT" > "$work/.horizon-revision"

  # Modul game dikompilasi oleh dua crate (biner horizon-client dan horizon-android lewat android_lib.rs), dan
  # horizon-mixins menolak tujuan modul yang dicapai lewat dua nama: selama merencanakan, pustaka horizon-android
  # berupa berkas kosong dan lib.rs kehilangan atribut bersyaratnya (tidak berpengaruh di Android).
  manifest=crates/horizon-android/Cargo.toml
  lib_line='path = "../horizon-client/src/android_lib.rs"'
  grep -qxF "$lib_line" "$work/$manifest" \
    || { echo "native_mods: $manifest tidak lagi berisi: $lib_line"; return 1; }
  sed -i 's|^path = "../horizon-client/src/android_lib.rs"$|path = "plan-stub.rs"|' "$work/$manifest" || return 1
  : > "$work/crates/horizon-android/plan-stub.rs"
  lib_attr='#![cfg_attr(not(target_os = "android"), forbid(unsafe_code))]'
  grep -qxF "$lib_attr" "$work/crates/horizon-client/src/lib.rs" \
    || { echo "native_mods: crates/horizon-client/src/lib.rs tidak lagi berisi: $lib_attr"; return 1; }
  grep -vxF "$lib_attr" "$work/crates/horizon-client/src/lib.rs" > "$WORK/native-lib.rs" || return 1
  cp "$WORK/native-lib.rs" "$work/crates/horizon-client/src/lib.rs" || return 1

  # Bundel, tiap satu dipatok ke commit yang dibangun (commit Starling bukan commit Horizon).
  for id in $NATIVE_MODS; do
    bundle="$SRC/native-mods/$id/mixins/$id"
    [ -f "$bundle/horizon-mixin.toml" ] \
      || { echo "native_mods: '$id' tidak ada di sumber (native-mods/$id/mixins/$id/horizon-mixin.toml)."; return 1; }
    cp -r "$bundle" "$mods/$id" || return 1
    sed -i -E "s|^horizon_revision = \"[0-9a-f]*\"|horizon_revision = \"$COMMIT\"|" "$mods/$id/horizon-mixin.toml" || return 1
  done
  python3 "$SRC/packaging/android/native_mod_adapt.py" "$work" "$mods" || return 1

  logged cargo fetch --locked --manifest-path "$work/Cargo.toml" || return 1
  logged "$tools/bin/horizon-mixins" apply --workspace "$work" --mods "$mods" --out "$stage" || return 1
  # Manifest horizon-android kembali membangun android_lib.rs.
  cp "$SRC/$manifest" "$stage/$manifest" || return 1

  rm -f "$LIB"
  compile_starling "$stage" || return 1
  [ -f "$LIB" ] || { echo "native_mods: kompilasi selesai tetapi pustaka tidak ada: $LIB"; return 1; }
  return 0
}

# Id yang diminta harus modul bawaan sumber (BUILTIN_NATIVE_MODS di build_apk.py, yang juga memeriksanya di tahap 3);
# diperiksa di sini supaya salah ketik gagal sekarang, bukan setelah kompilasi berjam-jam.
if [ "$LAYOUT" = starling ] && [ -n "$NATIVE_MODS" ]; then
  python3 - "$SRC/packaging/android/build_apk.py" > "$WORK/builtin-native-mods.txt" <<'PY' || die "BUILTIN_NATIVE_MODS tidak terbaca dari packaging/android/build_apk.py."
import ast, sys
tree = ast.parse(open(sys.argv[1], encoding="utf-8").read())
for node in tree.body:
    if isinstance(node, ast.Assign) and any(getattr(t, "id", "") == "BUILTIN_NATIVE_MODS" for t in node.targets):
        print(" ".join(ast.literal_eval(node.value)))
PY
  BUILTIN_IDS="$(cat "$WORK/builtin-native-mods.txt")"
  [ -n "$BUILTIN_IDS" ] || die "BUILTIN_NATIVE_MODS kosong atau tidak ditemukan di packaging/android/build_apk.py."
  for id in $NATIVE_MODS; do
    case " $BUILTIN_IDS " in
      *" $id "*) ;;
      *) die "native_mods: '$id' bukan modul bawaan sumber. Yang ada: $BUILTIN_IDS" ;;
    esac
  done
fi

NATIVE_COMPILED=""
NATIVE_STATE="tidak diminta"
NATIVE_NOTE=""
LIB_BUILT=0
if [ "$LAYOUT" = starling ] && [ -n "$NATIVE_MODS" ]; then
  phase 02b-mod-native "2b/6 Kompilasi Starling dengan mod native di dalamnya (horizon-mixins)"
  echo "Mod native diminta: $NATIVE_MODS"
  if native_mods_build; then
    NATIVE_COMPILED="$NATIVE_MODS"
    NATIVE_STATE="dibuat"
    LIB_BUILT=1
    echo "Mod native masuk ke pustaka: $NATIVE_COMPILED"
  else
    NATIVE_STATE="gagal"
    NATIVE_NOTE="$(sed -E 's/\x1b\[[0-9;]*[A-Za-z]//g' "$PHASE_LOG" 2> /dev/null | { grep -E '^(error|Error|ERROR)|native_mods:|panicked' || true; } | head -n 15)"
    echo "::warning title=Mod native Starling gagal dikompilasi::Mesin dibangun TANPA mod native; sisanya tetap jalan. Lihat tahap 2b di log."
    rm -f "$LIB"
  fi
fi

# ---- 3. Data ----
phase 03-data "3/6 Siapkan tabel data (unduh berkas Mojang sementara, jalankan eksporter)"
if [ "$LAYOUT" = starling ]; then
  echo "Starling menyiapkan data game di HP: tidak ada tabel data dan tidak ada berkas Mojang yang diunduh."
  # Aset APK Starling lewat fungsi sumber sendiri (build_apk.assets): prepare-kit.zip (eksporter dikompilasi dengan JDK),
  # lowbudget.zip, StarlingVisuals.zip (shader bawaan), font, dan mod native bawaan.
  STARLING_OUT="$WORK/starling-out"
  rm -rf "$STARLING_OUT"
  mkdir -p "$STARLING_OUT"
  (cd "$SRC" && logged env HORIZON_TELEMETRY_URL=off JAVA_HOME="$JDK" PATH="$JDK/bin:$PATH" \
    STARLING_APK_NATIVE_MODS="$NATIVE_COMPILED" python3 - "$SRC" "$STARLING_OUT" <<'PY'
import pathlib
import sys
sys.path.insert(0, sys.argv[1] + "/packaging/android")
import build_apk
build_apk.assets(pathlib.Path(sys.argv[2]))
PY
  )
  [ -f "$STARLING_OUT/assets/prepare-kit.zip" ] || die "aset Starling tidak terbentuk: prepare-kit.zip tidak ada."
  # Kelas Java dan resource yang HARUS cocok dengan pustaka native (fungsi JNI terikat pada nama kelasnya). Diambil dari
  # sumber SETELAH perubahan Amber diterapkan, dan dikemas bersama pustakanya supaya selalu satu commit. Hanya paket
  # dev/horizon/android (salinan io/github/... tidak dipakai pustaka) dan resource values (tema dan ikon milik Amber).
  FILES_STAGE="$WORK/starling-files"
  rm -rf "$FILES_STAGE" "$WORK/starling-files.zip"
  mkdir -p "$FILES_STAGE/java/dev/horizon" "$FILES_STAGE/res/values"
  cp -r "$SRC/packaging/android/java/dev/horizon/android" "$FILES_STAGE/java/dev/horizon/android"
  cp "$SRC"/packaging/android/res/values/*.xml "$FILES_STAGE/res/values/"
  rm -f "$FILES_STAGE/res/values/themes.xml"
  # Warna layar diagnostik dibuat saat build oleh build_apk.link_resources() dari palet gelap crates/horizon-theme: sama di sini.
  python3 - "$SRC" "$FILES_STAGE/res/values/starling-colors.xml" <<'PY'
import pathlib
import re
import sys
theme = pathlib.Path(sys.argv[1], "crates/horizon-theme/src/lib.rs").read_text(encoding="utf-8")
dark = theme.split("pub const DARK: Palette = Palette {", 1)[1].split("};", 1)[0]
colors = dict(re.findall(r"(\w+): 0x([0-9a-fA-F]{6})", dark))
names = ("page", "rule", "ink", "ink_soft", "accent", "on_accent")
entries = "\n".join(f'<color name="starling_{n}">#{colors[n]}</color>' for n in names)
pathlib.Path(sys.argv[2]).write_text(f"<resources>\n{entries}\n</resources>\n", encoding="utf-8")
PY
  cp -r "$STARLING_OUT/assets" "$FILES_STAGE/assets"
  (cd "$FILES_STAGE" && zip -q -X -r "$WORK/starling-files.zip" java res assets)
  echo "Berkas Starling untuk APK: $(wc -c < "$WORK/starling-files.zip" | tr -d ' ') byte ($(find "$FILES_STAGE" -type f | wc -l | tr -d ' ') berkas)"
else
MC_VERSION="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["minecraft"]["version"])' "$SRC/research-lock.json")"
RESOURCES="$SRC/.local/horizon-client/$MC_VERSION"
if [ -f "$RESOURCES/horizon-resources.json" ] && [ -f "$RESOURCES/worldgen/blocks/structure-blocks.json" ]; then
  echo "Tabel data untuk Minecraft $MC_VERSION sudah ada, dipakai ulang."
else
  # Lewat scripts/prepare-horizon-data.py: memakai berkas versi Mojang yang dipatok proyek sumber, bukan yang
  # kebetulan terbit ulang hari ini (horizon-prepare menolak yang tidak dikenalnya).
  (cd "$SRC" && logged env HORIZON_TELEMETRY_URL=off python3 "$ROOT/scripts/prepare-horizon-data.py" "$SRC" --java-home "$JDK")
fi
fi

# ---- 4. Kompilasi ----
phase 04-kompilasi "4/6 Kompilasi mesin Rust (arm64, profil $PROFILE)"
if [ "$LAYOUT" = starling ]; then
  # Sudah dikompilasi di tahap 2b bila mod native berhasil disusun; selain itu (tanpa mod, atau gagal) tanpa mod.
  if [ "$LIB_BUILT" = 1 ]; then
    echo "Pustaka sudah dikompilasi di tahap 2b (dengan mod native): $NATIVE_COMPILED"
  else
    compile_starling "$SRC"
  fi
else
  (cd "$SRC" && logged env CARGO_TARGET_DIR="$TARGET_DIR" HORIZON_TELEMETRY_URL=off python3 tools/android_build.py \
    --abi arm64-v8a --public --rust-only --profile "$PROFILE" --sdk "$SDK_MINI" --java-home "$JDK")
fi
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
if [ "$LAYOUT" = starling ]; then
  # Peluncur `java` dan `javac` untuk menyiapkan game di HP (packaging/android/native/java_launcher.c): sebuah aplikasi
  # hanya boleh menjalankan program dari folder pustakanya, jadi keduanya dikemas sebagai pustaka, bersama runtime C++.
  CLANG="$NDK_BIN/aarch64-linux-android${MIN_SDK}-clang"
  [ -x "$CLANG" ] || die "clang NDK tidak ditemukan: $CLANG"
  logged "$CLANG" -O2 -fPIE -pie -Wall -Werror -Wl,-z,max-page-size=16384 \
    -o "$STAGE/libstarling_java.so" "$SRC/packaging/android/native/java_launcher.c" -ldl
  logged "$CLANG" -O2 -fPIE -pie -Wall -Werror -DJAVAC -Wl,-z,max-page-size=16384 \
    -o "$STAGE/libstarling_javac.so" "$SRC/packaging/android/native/java_launcher.c" -ldl
  CXX_SHARED="$NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so"
  [ -f "$CXX_SHARED" ] || die "libc++_shared.so NDK tidak ditemukan: $CXX_SHARED"
  cp "$CXX_SHARED" "$STAGE/libc++_shared.so"
  logged bash "$ROOT/scripts/package-horizon-engine.sh" \
    --so "$STAGE/libhorizon_android.so" --license-dir "$SRC" --out "$OUT" \
    --extra "$STAGE/libstarling_java.so" --extra "$STAGE/libstarling_javac.so" --extra "$STAGE/libc++_shared.so" \
    --files-zip "$WORK/starling-files.zip" \
    --repo "$REPO" --commit "$COMMIT" --profile "$PROFILE" --patch-set "$PATCH_SET" --patch-hash "$PATCH_HASH" --rust "$(rustc --version)" --ndk "$NDK_VERSION" \
    --readelf "$NDK_BIN/llvm-readelf"
else
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
fi

# ---- 5b. Mesin 32-bit (armeabi-v7a), eksperimental ----
# Tidak pernah menggagalkan run: bila apa pun di sini gagal, paket 32-bit tidak dibuat dan Release terbit dengan paket
# arm64 saja. Build 32-bit proyek sumber: tanpa fitur bawaan kecuali audio (tanpa runtime mod .hmod), API 27.
ARM32_STATE="dilewati"
ARM32_NOTE=""
build_arm32() {
  local lib32 stage32
  logged rustup target add --toolchain "$RUST_CHANNEL" armv7-linux-androideabi || return 1
  (cd "$SRC" && logged env CARGO_TARGET_DIR="$TARGET_DIR" HORIZON_TELEMETRY_URL=off python3 tools/android_build.py \
    --abi armeabi-v7a --public --rust-only --profile "$PROFILE" --sdk "$SDK_MINI" --java-home "$JDK") || return 1
  lib32="$TARGET_DIR/armv7-linux-androideabi/$PROFILE/libhorizon_android.so"
  if [ ! -f "$lib32" ]; then
    echo "kompilasi selesai tetapi pustaka 32-bit tidak ada: $lib32"
    return 1
  fi
  echo "Pustaka mentah 32-bit: $(wc -c < "$lib32" | tr -d ' ') byte"
  stage32="$WORK/stage32"
  rm -rf "$stage32"
  mkdir -p "$stage32"
  cp "$lib32" "$stage32/libhorizon_android.so" || return 1
  logged "$NDK_BIN/llvm-strip" --strip-unneeded "$stage32/libhorizon_android.so" || return 1
  logged bash "$ROOT/scripts/package-horizon-engine.sh" --abi armeabi-v7a \
    --so "$stage32/libhorizon_android.so" --data "$DATA_ZIP" --license-dir "$SRC" --out "$OUT" \
    --repo "$REPO" --commit "$COMMIT" --profile "$PROFILE" --patch-set "$PATCH_SET" --patch-hash "$PATCH_HASH" --rust "$(rustc --version)" --ndk "$NDK_VERSION" \
    --readelf "$NDK_BIN/llvm-readelf" || return 1
}
if [ "$LAYOUT" = starling ]; then
  ARM32_NOTE="mesin Starling belum dibangun untuk 32-bit"
  echo "Mesin 32-bit: $ARM32_NOTE"
elif [ "${HORIZON_ENGINE_ARM32:-on}" = "off" ]; then
  ARM32_NOTE="dimatikan (HORIZON_ENGINE_ARM32=off)"
  echo "Mesin 32-bit: $ARM32_NOTE"
else
  phase 05b-arm32 "5b/6 Kompilasi mesin 32-bit (armeabi-v7a, eksperimental, profil $PROFILE)"
  if build_arm32; then
    ARM32_STATE="dibuat"
  else
    ARM32_STATE="gagal"
    rm -f "$OUT/$BUNDLE_NAME32" "$OUT/$BUNDLE_NAME32.sha256"
    ARM32_NOTE="$(sed -E 's/\x1b\[[0-9;]*[A-Za-z]//g' "$PHASE_LOG" 2> /dev/null | grep -E '^(error|Error|ERROR)|panicked|No such file|not found' | head -n 10 || true)"
    echo "::warning title=Mesin 32-bit gagal dibangun::Paket arm64 tetap terbit tanpa paket 32-bit. Baris galatnya ada di ringkasan run."
  fi
fi

# ---- 6. Terbitkan ----
if [ "$PUBLISH" = 1 ]; then
  phase 06-terbit "6/6 Terbitkan Release $TAG"
  if gh release view "$TAG" > /dev/null 2>&1; then
    logged gh release delete "$TAG" --cleanup-tag --yes
  fi
  NOTES="$WORK/release-notes.md"
  {
    if [ "$ARM32_STATE" = "dibuat" ]; then
      echo "Mesin Rust (Horizon) untuk Android arm64 dan armeabi-v7a (32-bit, eksperimental), dikompilasi dari sumber oleh workflow di repo ini."
    else
      echo "Mesin Rust (Horizon) untuk Android arm64, dikompilasi dari sumber oleh workflow di repo ini."
    fi
    echo
    echo "- Sumber: $REPO"
    echo "- Commit: \`$COMMIT\`"
    [ -z "$PATCH_SET" ] || echo "- Perubahan Amber pada sumber: \`$PATCH_SET\`, isi \`$PATCH_HASH\` (third_party/horizon-patches/$PATCH_SET)"
    [ -z "$NATIVE_COMPILED" ] || echo "- Mod native di dalam pustaka: $NATIVE_COMPILED"
    echo "- Profil: $PROFILE, NDK $NDK_VERSION, $(rustc --version)"
    echo
    echo "**Ini bukan rilis aplikasi.** Berkas di sini dipasang otomatis ke APK oleh build GitHub Actions"
    echo "(scripts/fetch-horizon-engine.sh); tidak perlu diunduh atau dipasang sendiri."
  } > "$NOTES"
  TARGET_SHA="${GITHUB_SHA:-$(git -C "$ROOT" rev-parse HEAD)}"
  # Pra-rilis dengan tag bukan nomor versi: tidak dianggap pembaruan aplikasi dan tidak mengubah penomoran versi.
  ASSETS=("$OUT/$BUNDLE_NAME" "$OUT/$BUNDLE_NAME.sha256")
  if [ "$ARM32_STATE" = "dibuat" ]; then
    ASSETS+=("$OUT/$BUNDLE_NAME32" "$OUT/$BUNDLE_NAME32.sha256")
  fi
  logged gh release create "$TAG" "${ASSETS[@]}" \
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
  echo "### Mesin Rust ($ENGINE_LABEL) berhasil dibangun"
  echo
  echo "- Sumber: \`$REPO\` commit \`${COMMIT:0:12}\` ($PROFILE)${PATCH_SET:+, perubahan Amber: $PATCH_SET}"
  echo "- Alat: $(val rust), NDK $NDK_VERSION${JAVA_HAVE:+, javac $JAVA_HAVE}"
  echo "- \`libhorizon_android.so\` (di-strip): $(val so_bytes) byte, sha256 \`$(val so_sha256)\`"
  if [ "$LAYOUT" = starling ]; then
    echo "- Pustaka pendamping: $(val extras)"
    case "$NATIVE_STATE" in
      dibuat) echo "- Mod native di dalam pustaka: $NATIVE_COMPILED" ;;
      gagal)
        echo "- Mod native: **GAGAL dikompilasi**; mesin dibangun TANPA mod (diminta: $NATIVE_MODS). Layar Mod Amber akan menampilkan tanpa modul bawaan."
        if [ -n "$NATIVE_NOTE" ]; then
          echo
          echo "Baris galat tahap 2b:"
          echo
          echo '```'
          printf '%s\n' "$NATIVE_NOTE"
          echo '```'
        fi
        ;;
      *) echo "- Mod native: tidak diminta (native_mods kosong), pustaka tanpa mod." ;;
    esac
  else
    echo "- \`engine-data.zip\`: $(val data_bytes) byte"
  fi
  echo "- Paket arm64: $(wc -c < "$OUT/$BUNDLE_NAME" | tr -d ' ') byte, sha256 \`$(cut -d' ' -f1 "$OUT/$BUNDLE_NAME.sha256")\`"
  case "$ARM32_STATE" in
    dibuat) echo "- Paket 32-bit (armeabi-v7a, eksperimental): $(wc -c < "$OUT/$BUNDLE_NAME32" | tr -d ' ') byte, sha256 \`$(cut -d' ' -f1 "$OUT/$BUNDLE_NAME32.sha256")\`" ;;
    gagal)
      echo "- Paket 32-bit: **GAGAL dibangun** (paket arm64 tidak terpengaruh; APK 32-bit dibangun tanpa mesin Rust)."
      if [ -n "$ARM32_NOTE" ]; then
        echo
        echo "Baris galat mesin 32-bit:"
        echo
        echo '```'
        printf '%s\n' "$ARM32_NOTE"
        echo '```'
      fi
      ;;
    *) echo "- Paket 32-bit: dilewati, $ARM32_NOTE." ;;
  esac
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
