#!/usr/bin/env bash
# Mengompilasi mesin Rust Amber (Starling, MIT) dari SUMBER untuk Android, lalu (opsional) menerbitkannya sebagai
# Release di repo ini. Dijalankan oleh GitHub Actions (Linux x86-64) bila patokan di horizon-engine.properties berubah;
# build APK biasa hanya mengambil hasilnya (scripts/fetch-horizon-engine.sh). Nama berkas dipertahankan untuk workflow.
#
# Caranya sama dengan CI resmi Starling: Rust yang dipatok tools/ci/rust-version, cargo-ndk 3.5.4, dan fungsi
# build_library / build_launchers / build_adrenotools / assets dari packaging/android/build_apk.py.
#
# Urutan: sumber + patch Amber, alat, aset, pustaka arm64 TANPA mod (hasil yang pasti), mod native (bonus: kegagalannya
# hanya membuang mod yang bermasalah), terbitkan, lalu armeabi-v7a (eksperimental, tidak pernah menggagalkan apa pun).
# Semua tahap opsional dibatasi waktu supaya job 120 menit tidak habis sebelum mesin terbit.
#
# Konfigurasi: horizon-engine.properties. Variabel lingkungan: HORIZON_ENGINE_SOURCE_REPO, _COMMIT, _PROFILE,
# _PATCH_SET, _WORK, _JDK, _JOBS, _FORCE=true, _ARM32=off, _MODS=off, _DEADLINE_MIN (bawaan 105).
#
# Pemakaian:
#   scripts/build-horizon-engine.sh [--publish] [--force] [--src FOLDER] [--work FOLDER]
#   scripts/build-horizon-engine.sh --print-tag

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONFIG="${HORIZON_ENGINE_CONFIG:-$ROOT/horizon-engine.properties}"
BUNDLE_NAME="starling-engine-arm64-v8a.zip"
BUNDLE_NAME32="starling-engine-armeabi-v7a.zip"

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
      [ $# -ge 2 ] || die "$1 butuh nilai."
      if [ "$1" = "--src" ]; then SRC_ARG="$2"; else WORK="$2"; fi
      shift 2
      ;;
    -h | --help)
      awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"
      exit 0
      ;;
    *) die "argumen tidak dikenal: $1" ;;
  esac
done

REPO="${HORIZON_ENGINE_SOURCE_REPO:-$(prop source_repo)}"
COMMIT="$(printf '%s' "${HORIZON_ENGINE_COMMIT:-$(prop commit)}" | tr 'A-F' 'a-f' | tr -d '[:space:]')"
# Tanda Release memakai profil dari berkas konfigurasi, bukan dari variabel lingkungan: engine yang dibangun manual
# dengan profil lain tetap ditemukan oleh build APK.
PROFILE_TAG="$(prop profile)"
PROFILE_TAG="${PROFILE_TAG:-fast}"
PROFILE="${HORIZON_ENGINE_PROFILE:-$PROFILE_TAG}"
PATCH_SET="$(printf '%s' "${HORIZON_ENGINE_PATCH_SET-$(prop patch_set)}" | tr 'A-Z' 'a-z' | tr -d '[:space:]')"
NDK_VERSION="$(prop ndk)"
NDK_VERSION="${NDK_VERSION:-28.2.13676358}"
REVISION="$(prop revision)"
MODS_REPO="$(prop mods_repo)"
MODS_COMMIT="$(prop mods_commit)"
NATIVE_MODS="$(prop native_mods | tr 'A-Z,' 'a-z ' | tr -s '[:space:]' ' ' | sed -E 's/^ +//; s/ +$//')"

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

# Memeriksa konfigurasi lalu mengisi PATCH_DIR, PATCH_HASH, dan TAG.
check_config() {
  [ -n "$REPO" ] || die "source_repo belum diisi di horizon-engine.properties."
  case "$REPO" in https://*) ;; *) die "source_repo harus diawali https://" ;; esac
  [[ $COMMIT =~ ^[0-9a-f]{40}$ ]] || die "commit harus 40 digit heksadesimal (isi commit= di horizon-engine.properties)."
  case "$PROFILE" in release | fast) ;; *) die "profile harus release atau fast." ;; esac
  case "$PROFILE_TAG" in release | fast) ;; *) die "profile harus release atau fast." ;; esac
  [[ -z $PATCH_SET || $PATCH_SET =~ ^[a-z0-9]{1,20}$ ]] || die "patch_set hanya boleh huruf kecil dan angka (paling banyak 20 karakter)."
  local re='^[a-z0-9_]{1,64}( [a-z0-9_]{1,64})*$'
  [[ -z $NATIVE_MODS || $NATIVE_MODS =~ $re ]] || die "native_mods hanya boleh berisi id (huruf kecil, angka, garis bawah)."
  if [ -n "$NATIVE_MODS" ]; then
    [[ $MODS_REPO == https://* && $MODS_COMMIT =~ ^[0-9a-f]{40}$ ]] || die "native_mods butuh mods_repo (https) dan mods_commit (40 digit)."
  fi
  PATCH_DIR="" PATCH_HASH=""
  if [ -n "$PATCH_SET" ]; then
    PATCH_DIR="$ROOT/third_party/horizon-patches/$PATCH_SET"
    ls "$PATCH_DIR"/*.diff > /dev/null 2>&1 \
      || die "patch_set=$PATCH_SET tidak punya berkas .diff di third_party/horizon-patches/$PATCH_SET/."
    PATCH_HASH="$(patch_hash "$PATCH_DIR")"
  fi
  TAG="starling-engine-${COMMIT:0:12}${PATCH_SET:+-$PATCH_SET-$PATCH_HASH}"
  [ "$PROFILE_TAG" = "release" ] || TAG="$TAG-$PROFILE_TAG"
  [ -z "$REVISION" ] || TAG="$TAG-r$REVISION"
  [ -z "$NATIVE_MODS" ] || TAG="$TAG-m$(printf '%s %s' "$MODS_COMMIT" "$NATIVE_MODS" | sha256sum | cut -c1-6)"
}

check_config
if [ "$PRINT_TAG" = 1 ]; then
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
  echo "::error title=Mesin Rust (Starling) gagal dibangun::Berhenti di tahap: $PHASE (kode $rc). Ujung lognya ada di ringkasan run."
  {
    echo "### Mesin Rust (Starling) GAGAL dibangun"
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


[ "$(uname -s)" = "Linux" ] && [ "$(uname -m)" = "x86_64" ] || die "skrip ini hanya untuk Linux x86-64 (runner GitHub Actions)."
echo "Sumber: $REPO @ $COMMIT ($PROFILE), perubahan Amber: ${PATCH_SET:-tanpa}${PATCH_HASH:+ ($PATCH_HASH)}, tag Release: $TAG"

if [ "$PUBLISH" = 1 ]; then
  command -v gh > /dev/null 2>&1 || die "--publish butuh perintah gh."
  [ -n "${GH_TOKEN:-${GITHUB_TOKEN:-}}" ] || die "--publish butuh GH_TOKEN."
  if gh release view "$TAG" > /dev/null 2>&1; then
    if [ "$FORCE" = 1 ]; then
      echo "Release $TAG sudah ada, akan diganti (--force)."
    else
      echo "Release $TAG sudah ada: tidak perlu membangun lagi (pakai force untuk membangun ulang)."
      printf '### Mesin Rust (Starling): sudah ada\n\nRelease `%s` sudah ada, tidak ada yang dibangun.\n' "$TAG" | summary
      PHASE="selesai"
      exit 0
    fi
  fi
fi

DEADLINE=$(( ${HORIZON_ENGINE_DEADLINE_MIN:-105} * 60 ))
time_left() { echo $((DEADLINE - (SECONDS - STARTED))); }

# ---- 1. Sumber ----
phase 01-sumber "1/6 Ambil sumber (commit dipatok) dan terapkan perubahan Amber"
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
for f in Cargo.toml research-lock.json tools/ci/rust-version crates/horizon-theme/src/lib.rs packaging/android/build_apk.py LICENSE THIRD_PARTY_NOTICES.md; do
  [ -f "$SRC/$f" ] || die "sumber tidak berisi $f: bentuk repo sumber berubah, patokan perlu ditinjau."
done
if [ -n "$PATCH_DIR" ]; then
  for p in "$PATCH_DIR"/*.diff; do
    if git -C "$SRC" apply --check "$p" > /dev/null 2>&1; then
      logged git -C "$SRC" apply "$p"
      echo "Perubahan Amber diterapkan: ${p##*/}"
    elif git -C "$SRC" apply --reverse --check "$p" > /dev/null 2>&1; then
      echo "Perubahan Amber sudah terpasang: ${p##*/}"
    else
      git -C "$SRC" apply --check "$p" 2>&1 | tee -a "$PHASE_LOG" || true
      die "${p##*/} tidak cocok dengan sumber pada commit yang dipatok."
    fi
  done
fi
# Launcher mengunduh berkas resmi Minecraft sendiri dengan patokan di starling-pins.json: harus sama dengan sumber.
python3 - "$SRC/research-lock.json" "$ROOT/app/src/main/assets/starling-pins.json" <<'PY' || die "app/src/main/assets/starling-pins.json tidak sama dengan research-lock.json sumber."
import json, sys
lock = json.load(open(sys.argv[1]))["minecraft"]
pins = json.load(open(sys.argv[2]))
bad = [k for k in ("version", "manifest_sha1", "accepted_manifest_sha1", "client_sha1", "server_sha1") if lock.get(k) != pins.get(k)]
if bad:
    sys.exit("berbeda: " + ", ".join(bad))
PY

# ---- 2. Alat ----
phase 02-alat "2/6 Siapkan alat (JDK, SDK/NDK, Rust, cargo-ndk, swap)"
python3 -c 'import sys; sys.exit(0 if sys.version_info >= (3, 11) else 1)' || die "butuh Python 3.11 atau lebih baru."
JDK="${HORIZON_ENGINE_JDK:-${JAVA_HOME:-}}"
[ -n "$JDK" ] && [ -x "$JDK/bin/javac" ] || die "JDK dengan javac tidak ditemukan (JAVA_HOME ke JDK 25)."
JAVA_NEED="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["minecraft"]["java_major"])' "$SRC/research-lock.json")"
JAVA_HAVE="$("$JDK/bin/javac" -version 2>&1 | sed -n 's/^javac \([0-9]*\).*/\1/p' | head -n 1)"
[ -n "$JAVA_HAVE" ] && [ "$JAVA_HAVE" -ge "$JAVA_NEED" ] || die "butuh JDK $JAVA_NEED atau lebih baru; yang ada: ${JAVA_HAVE:-?}."

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
NDK_HOME="$SDK_ROOT/ndk/$NDK_VERSION"
if [ ! -d "$NDK_HOME" ] || [ ! -f "$SDK_ROOT/platforms/android-36/android.jar" ] || [ ! -d "$SDK_ROOT/build-tools/35.0.0" ]; then
  # "yes" berakhir dengan SIGPIPE; hasil sebenarnya dicek di bawah.
  yes | "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" "build-tools;35.0.0" "platforms;android-36" "ndk;$NDK_VERSION" > /dev/null || true
fi
NDK_BIN="$NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
[ -x "$NDK_BIN/clang" ] || die "NDK $NDK_VERSION tidak ditemukan di $SDK_ROOT/ndk."
[ -f "$SDK_ROOT/platforms/android-36/android.jar" ] || die "platform android-36 tidak terpasang."
export ANDROID_HOME="$SDK_ROOT" ANDROID_NDK_HOME="$NDK_HOME" JAVA_HOME="$JDK" PATH="$JDK/bin:$PATH"

command -v rustup > /dev/null 2>&1 || die "rustup tidak ditemukan."
RUST_CHANNEL="$(tr -d '[:space:]' < "$SRC/tools/ci/rust-version")"
logged rustup toolchain install "$RUST_CHANNEL" --profile minimal --component rust-src --no-self-update
logged rustup target add --toolchain "$RUST_CHANNEL" aarch64-linux-android
export RUSTUP_TOOLCHAIN="$RUST_CHANNEL"
echo "Rust: $(rustc --version)"
cargo ndk --version > /dev/null 2>&1 || logged cargo install cargo-ndk --version 3.5.4 --locked
export HORIZON_TELEMETRY_URL=off
# Dibaca patch Amber saat kompilasi (option_env!).
export AMBER_EMBEDDED=1
unset AMBER_BUNDLED_MODS
[ -z "${HORIZON_ENGINE_JOBS:-}" ] || export CARGO_BUILD_JOBS="$HORIZON_ENGINE_JOBS"

# Seperti CI resmi Starling: kompilasi crate game butuh lebih dari 16 GB RAM, jadi tambah swap dan batasi proses.
if [ -n "${GITHUB_ACTIONS:-}" ] && sudo -n true 2> /dev/null; then
  avail="$(df --output=avail -BG /mnt 2> /dev/null | tail -1 | tr -dc 0-9)"
  if [ "${avail:-0}" -ge 45 ] && [ ! -e /mnt/amber-engine.swap ]; then
    { sudo fallocate -l 16G /mnt/amber-engine.swap && sudo chmod 600 /mnt/amber-engine.swap \
      && sudo mkswap /mnt/amber-engine.swap > /dev/null && sudo swapon /mnt/amber-engine.swap; } || echo "swap tidak bisa dibuat"
  fi
  export CARGO_BUILD_JOBS="${HORIZON_ENGINE_JOBS:-3}"
fi

# ---- 3. Aset ----
phase 03-aset "3/6 Susun aset APK Starling (penyiapan game di HP)"
APK_OUT="$WORK/apk-out"
rm -rf "$APK_OUT"
mkdir -p "$APK_OUT"
(cd "$SRC" && logged python3 - "$SRC" "$APK_OUT" <<'PY'
import pathlib, sys
sys.path.insert(0, sys.argv[1] + "/packaging/android")
import build_apk
build_apk.assets(pathlib.Path(sys.argv[2]))
PY
)
[ -f "$APK_OUT/assets/prepare-kit.zip" ] || die "aset Starling tidak terbentuk: prepare-kit.zip tidak ada."

# Zip kelas Java, resource, dan aset; $1 = id mod yang ada di dalam pustaka (boleh kosong). Kelas Java harus satu commit
# dengan pustaka (fungsi JNI terikat nama kelasnya). themes.xml dibuang: manifest Amber memakai temanya sendiri.
make_files_zip() {
  local stage="$WORK/starling-files"
  rm -rf "$stage" "$WORK/starling-files.zip"
  mkdir -p "$stage/java" "$stage/res/values" || return 1
  cp -r "$SRC/packaging/android/java/io" "$stage/java/io" || return 1
  cp "$SRC"/packaging/android/res/values/*.xml "$stage/res/values/" || return 1
  rm -f "$stage/res/values/themes.xml"
  python3 - "$SRC/crates/horizon-theme/src/lib.rs" "$stage/res/values/starling-colors.xml" <<'PY' || return 1
import pathlib, re, sys
theme = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
dark = theme.split("pub const DARK: Palette = Palette {", 1)[1].split("};", 1)[0]
colors = dict(re.findall(r"(\w+): 0x([0-9a-fA-F]{6})", dark))
names = ("page", "rule", "ink", "ink_soft", "accent", "on_accent")
entries = "\n".join(f'<color name="starling_{n}">#{colors[n]}</color>' for n in names)
pathlib.Path(sys.argv[2]).write_text(f"<resources>\n{entries}\n</resources>\n", encoding="utf-8")
PY
  cp -r "$APK_OUT/assets" "$stage/assets" || return 1
  mkdir -p "$stage/assets/native-mods"
  # shellcheck disable=SC2086
  printf '%s\n' $1 > "$stage/assets/native-mods/compiled.txt"
  (cd "$stage" && zip -q -X -r "$WORK/starling-files.zip" java res assets)
}

# Mengompilasi pustaka arsitektur $1 dan pendampingnya ke folder $2, dengan fungsi build_apk.py. Batas waktu $3 detik
# (0 = tanpa batas). Dipanggil juga di dalam `if`, jadi tiap langkah diakhiri `|| return 1`.
build_abi() {
  local abi="$1" dest="$2" limit="$3" triple lib name
  triple=aarch64-linux-android
  [ "$abi" = arm64-v8a ] || triple=armv7-linux-androideabi
  rm -rf "$dest"
  mkdir -p "$dest/tmp"
  cat > "$WORK/build_abi.py" <<'PY'
import pathlib, sys
source, dest, abi, profile, ndk, sdk = sys.argv[1:]
sys.path.insert(0, source + "/packaging/android")
import build_apk
ndk, dest = pathlib.Path(ndk), pathlib.Path(dest)
build_apk.build_library(profile, ndk, pathlib.Path(sdk), abi)
libraries = build_apk.build_launchers(ndk, dest / "tmp", abi)
libraries.update(build_apk.build_adrenotools(ndk, dest / "tmp", abi))
(dest / "extras.txt").write_text("".join(f"{path}\n" for path in libraries.values()))
PY
  (cd "$SRC" && logged timeout "$limit" python3 "$WORK/build_abi.py" "$SRC" "$dest" "$abi" "$PROFILE" "$NDK_HOME" "$SDK_ROOT") || return 1
  lib="$SRC/target/$triple/$PROFILE/libhorizon_android.so"
  [ -f "$lib" ] || { echo "pustaka tidak ada: $lib"; return 1; }
  if "$NDK_BIN/llvm-nm" -D --undefined-only "$lib" | grep -qw __emutls_get_address; then
    echo "pustaka meninggalkan __emutls_get_address tanpa definisi (tidak akan bisa dimuat di HP)"
    return 1
  fi
  "$NDK_BIN/llvm-strip" --strip-unneeded -o "$dest/libhorizon_android.so" "$lib" || return 1
  while IFS= read -r name; do cp "$name" "$dest/" || return 1; done < "$dest/extras.txt"
}

# Mengemas folder $1 (hasil build_abi, arsitektur $2) dengan pustaka utama $3 dan daftar mod $4.
package() {
  local dir="$1" abi="$2" so="$3" mods="$4" extras=() path
  make_files_zip "$mods" || return 1
  while IFS= read -r path; do extras+=(--extra "$dir/${path##*/}"); done < "$dir/extras.txt"
  logged bash "$ROOT/scripts/package-horizon-engine.sh" --abi "$abi" --so "$so" "${extras[@]}" \
    --files-zip "$WORK/starling-files.zip" --license-dir "$SRC" --out "$OUT" --repo "$REPO" --commit "$COMMIT" \
    --profile "$PROFILE" --patch-set "$PATCH_SET" --patch-hash "$PATCH_HASH" --rust "$(rustc --version)" \
    --ndk "$NDK_VERSION" --readelf "$NDK_BIN/llvm-readelf"
}

# ---- 4. Pustaka arm64 tanpa mod: hasil yang pasti ----
phase 04-kompilasi "4/6 Kompilasi mesin Rust arm64 (profil $PROFILE)"
build_abi arm64-v8a "$WORK/arm64" 0 || die "kompilasi mesin arm64 gagal."
package "$WORK/arm64" arm64-v8a "$WORK/arm64/libhorizon_android.so" ""

# ---- 5. Mod native (bonus, tidak pernah menggagalkan build) ----
NATIVE_COMPILED=""
NATIVE_DROPPED=""
NATIVE_NOTE=""
MODS_SRC="$WORK/mods-src"
BUNDLES="$WORK/mods-bundles"
CACHE="$WORK/native-cache"

# Menjalankan native_build.sh milik sumber untuk kumpulan mod $1. $2 = check (hanya periksa tipe) atau build.
native_run() {
  local group="$1" mode="$2" args=() id left check=""
  left="$(time_left)"
  [ "$left" -gt 120 ] || { echo "waktu habis"; return 1; }
  [ "$mode" != check ] || check=1
  for id in $group; do args+=("$BUNDLES/$id"); done
  # Saat memeriksa, AMBER_BUNDLED_MODS dibiarkan tetap supaya crate di bawah mod tidak dikompilasi ulang tiap kali.
  local bundled="$group"
  [ "$mode" != check ] || bundled="$NATIVE_MODS"
  rm -rf "$WORK/native-out"
  env NATIVE_CACHE="$CACHE" NATIVE_LIBRARY_ONLY=1 NATIVE_CHECK_ONLY="$check" PROFILE="$PROFILE" ABI=arm64-v8a \
    AMBER_BUNDLED_MODS="$bundled" HORIZON_ANDROID_BUILD_COMMIT="$COMMIT" \
    timeout --kill-after=60 "$left" bash "$MODS_SRC/packaging/android/native_build.sh" "$MODS_SRC" "$WORK/native-out" "${args[@]}" \
    > "$WORK/native-run.log" 2>&1
  local rc=$?
  cat "$WORK/native-run.log" >> "$PHASE_LOG"
  tail -n 40 "$WORK/native-run.log"
  return "$rc"
}

# Id yang disebut di baris galat log terakhir (nama mod atau nama modulnya): dicoba terpisah lebih dulu.
suspects() {
  local id names errors
  errors="$(grep -E -A6 '^(error|Error)|E_[A-Z_]+' "$WORK/native-run.log" 2> /dev/null || true)"
  for id in $1; do
    names="$id $(sed -n 's/^name = "\([A-Za-z0-9_]*\)"/\1/p' "$BUNDLES/$id/horizon-mixin.toml" | tr '\n' ' ')"
    local n
    for n in $names; do
      if grep -qw -- "$n" <<< "$errors"; then printf '%s ' "$id"; break; fi
    done
  done
}

native_mods_phase() {
  local id
  [ -f "$SRC/packaging/android/native_build.sh" ] && grep -q NATIVE_CHECK_ONLY "$SRC/packaging/android/native_build.sh" \
    || { NATIVE_NOTE="native_build.sh tidak ada atau belum diberi patch Amber"; return 1; }
  rm -rf "$MODS_SRC" "$BUNDLES" "$WORK/mods-repo"
  mkdir -p "$BUNDLES" "$CACHE/build-arm64-v8a"
  # native_build.sh membaca commit bersih: salinan sumber yang sudah diberi patch, di-commit sementara.
  mkdir -p "$MODS_SRC"
  (cd "$SRC" && tar --exclude=./target -cf - .) | tar -xf - -C "$MODS_SRC" || return 1
  git -C "$MODS_SRC" add -A || return 1
  git -C "$MODS_SRC" -c user.name=amber -c user.email=amber@localhost -c core.hooksPath=/dev/null \
    commit -q --allow-empty -m "Perubahan Amber (sementara)" || return 1
  git init -q "$WORK/mods-repo" || return 1
  logged git -C "$WORK/mods-repo" fetch -q --depth 1 "$MODS_REPO" "$MODS_COMMIT" || { NATIVE_NOTE="bundel mod tidak bisa diunduh"; return 1; }
  local present=""
  for id in $NATIVE_MODS; do
    mkdir -p "$BUNDLES/$id"
    if git -C "$WORK/mods-repo" archive "$MODS_COMMIT" "native-mods/$id/mixins/$id" 2> /dev/null \
      | tar -x -C "$BUNDLES/$id" --strip-components=4 && [ -f "$BUNDLES/$id/horizon-mixin.toml" ]; then
      present="$present $id"
    else
      NATIVE_DROPPED="$NATIVE_DROPPED $id"
    fi
  done
  present="${present# }"
  [ -n "$present" ] || { NATIVE_NOTE="tidak ada bundel mod"; return 1; }
  # Dependensi yang sudah dikompilasi di tahap 4 dipakai lagi.
  if [ -d "$SRC/target" ] && [ ! -e "$CACHE/build-arm64-v8a/target" ]; then mv "$SRC/target" "$CACHE/build-arm64-v8a/target"; fi

  echo "Mencoba semua mod sekaligus: $present"
  if native_run "$present" build; then
    NATIVE_COMPILED="$present"
    return 0
  fi
  NATIVE_NOTE="$(grep -E '^(error|Error)|E_[A-Z_]+|native_(build|mod_adapt|mixins_source):' "$WORK/native-run.log" | head -n 15 || true)"

  # Cari mod yang bermasalah: tersangka dari log diperiksa satu-satu, sisanya sekaligus; kumpulan yang gagal dibelah.
  local bad rest="" good="" queue=() group words n
  bad="$(suspects "$present")"
  for id in $present; do case " $bad " in *" $id "*) ;; *) rest="$rest $id" ;; esac; done
  rest="${rest# }"
  if [ -n "$bad" ] && [ -n "$rest" ]; then
    queue=("$rest")
    for id in $bad; do queue+=("$id"); done
  else
    read -r -a words <<< "$present"
    n=$(((${#words[@]} + 1) / 2))
    queue=("${words[*]:0:n}")
    [ "${#words[@]}" -le "$n" ] || queue+=("${words[*]:n}")
  fi
  # Sisakan waktu untuk satu kompilasi penuh setelah pemeriksaan (25 menit).
  while [ "${#queue[@]}" -gt 0 ] && [ "$(time_left)" -gt 1500 ]; do
    group="${queue[0]}"
    queue=("${queue[@]:1}")
    echo "Memeriksa: $group"
    if native_run "$group" check; then
      good="$good $group"
      continue
    fi
    read -r -a words <<< "$group"
    if [ "${#words[@]}" -le 1 ]; then
      NATIVE_DROPPED="$NATIVE_DROPPED $group"
    else
      n=$(((${#words[@]} + 1) / 2))
      queue=("${words[*]:0:n}" "${words[*]:n}" ${queue[@]+"${queue[@]}"})
    fi
  done
  for group in ${queue[@]+"${queue[@]}"}; do NATIVE_DROPPED="$NATIVE_DROPPED $group"; done
  good="${good# }"
  [ -n "$good" ] || return 1
  echo "Kompilasi dengan mod yang lolos: $good"
  if native_run "$good" build; then
    NATIVE_COMPILED="$good"
    return 0
  fi
  NATIVE_DROPPED="$NATIVE_DROPPED $good"
  return 1
}

if [ -n "$NATIVE_MODS" ] && [ "${HORIZON_ENGINE_MODS:-on}" != "off" ]; then
  phase 05-mod "5/6 Mod native (bonus): $NATIVE_MODS"
  if native_mods_phase && [ -f "$WORK/native-out/libhorizon_android.so" ]; then
    rm -rf "$WORK/out-polos"
    cp -r "$OUT" "$WORK/out-polos"
    if package "$WORK/arm64" arm64-v8a "$WORK/native-out/libhorizon_android.so" "$NATIVE_COMPILED"; then
      echo "Mod di dalam pustaka: $NATIVE_COMPILED"
    else
      rm -rf "$OUT"
      mv "$WORK/out-polos" "$OUT"
      NATIVE_COMPILED=""
    fi
  else
    NATIVE_COMPILED=""
    echo "::warning title=Mod native gagal::Mesin diterbitkan TANPA mod native; sisanya tidak terpengaruh."
  fi
fi

NOTES="$WORK/release-notes.md"
{
  echo "Mesin Rust Amber (Starling), dikompilasi dari sumber oleh workflow di repo ini."
  echo
  echo "- Sumber: $REPO commit \`$COMMIT\`"
  [ -z "$PATCH_SET" ] || echo "- Perubahan Amber: \`$PATCH_SET\`, isi \`$PATCH_HASH\`"
  echo "- Mod native di dalam pustaka: ${NATIVE_COMPILED:-tidak ada}"
  echo "- Profil: $PROFILE, NDK $NDK_VERSION, $(rustc --version)"
  echo
  echo "**Ini bukan rilis aplikasi.** Berkas di sini dipasang otomatis ke APK oleh build GitHub Actions."
} > "$NOTES"

# ---- 6. Terbitkan, lalu 32-bit ----
if [ "$PUBLISH" = 1 ]; then
  phase 06-terbit "6/6 Terbitkan Release $TAG"
  if gh release view "$TAG" > /dev/null 2>&1; then logged gh release delete "$TAG" --cleanup-tag --yes; fi
  logged gh release create "$TAG" "$OUT/$BUNDLE_NAME" "$OUT/$BUNDLE_NAME.sha256" --prerelease \
    --target "${GITHUB_SHA:-$(git -C "$ROOT" rev-parse HEAD)}" --title "Mesin Rust (Starling) ${COMMIT:0:12}" --notes-file "$NOTES"
else
  echo "Tanpa --publish: paket ada di $OUT"
fi

ARM32_STATE="dilewati"
build_arm32() {
  logged rustup target add --toolchain "$RUST_CHANNEL" armv7-linux-androideabi || return 1
  build_abi armeabi-v7a "$WORK/arm32" "$(time_left)" || return 1
  package "$WORK/arm32" armeabi-v7a "$WORK/arm32/libhorizon_android.so" "" || return 1
  [ "$PUBLISH" != 1 ] || logged gh release upload "$TAG" "$OUT/$BUNDLE_NAME32" "$OUT/$BUNDLE_NAME32.sha256" --clobber || return 1
}
if [ "${HORIZON_ENGINE_ARM32:-on}" != "off" ] && [ "$(time_left)" -gt 1500 ]; then
  phase 07-arm32 "Tambahan: mesin 32-bit (armeabi-v7a, eksperimental)"
  # Ruang disk: hasil kompilasi arm64 tidak dipakai lagi.
  rm -rf "$CACHE" "$SRC/target"
  if build_arm32; then
    ARM32_STATE="dibuat"
  else
    ARM32_STATE="gagal"
    echo "::warning title=Mesin 32-bit gagal::Mesin arm64 tidak terpengaruh."
  fi
fi
end_phase
PHASE="selesai"

{
  echo "### Mesin Rust (Starling) berhasil dibangun"
  echo
  echo "- Sumber: \`$REPO\` commit \`${COMMIT:0:12}\` ($PROFILE), perubahan Amber: ${PATCH_SET:-tanpa}"
  echo "- Mod native di dalam pustaka: ${NATIVE_COMPILED:-**tidak ada**}"
  [ -z "$NATIVE_DROPPED" ] || echo "- Mod yang dibuang:$NATIVE_DROPPED"
  if [ -n "$NATIVE_NOTE" ]; then
    echo
    echo "Galat pertama saat menyusun mod:"
    echo
    echo '```'
    printf '%s\n' "$NATIVE_NOTE"
    echo '```'
  fi
  echo "- Paket 32-bit: $ARM32_STATE"
  [ "$PUBLISH" != 1 ] || echo "- Release: \`$TAG\` (pra-rilis)"
  echo
  echo "| Tahap | Waktu |"
  echo "|---|---|"
  for t in "${TIMES[@]}"; do echo "| ${t%%|*} | $(fmt_time "${t##*|}") |"; done
  echo
  echo "Total: $(fmt_time $((SECONDS - STARTED)))"
} | tee /dev/stderr | summary
