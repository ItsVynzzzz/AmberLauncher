#!/usr/bin/env bash
# Membangun APK 32-bit (armeabi-v7a) AmberLauncher. Dijalankan oleh GitHub Actions
# (langkah "Bangun APK 32-bit" di .github/workflows/build-apk.yml) setelah APK 64-bit selesai.
#
# Kenapa lewat skrip dan bukan berkas di repo: pustaka dan runtime 32-bit totalnya >100 MB,
# jadi diambil saat build. Semua dikerjakan di SALINAN repo ($WORK), sehingga pohon kerja asli
# (yang dipakai langkah Release dan commit patch) tidak berubah sama sekali.
#
# Sumber tiap bagian 32-bit:
#   - Runtime Java (bin-arm.tar.xz)    : riwayat git repo ini (dihapus di commit aac1cbd).
#   - libmobileglues(+info_getter)     : riwayat git repo ini (commit awal).
#   - ANGLE, OSMesa, gl4es, shaderconv,
#     jnidispatch, unpack200           : proyek hulu ZalithLauncher2 (jniLibs/armeabi-v7a).
#   - pojavexec, exithook, linkerhook,
#     driver_helper, vulkan_check, awt  : dibangun dari kode C hulu dengan NDK (Android.mk).
#   - SDL, OpenAL, kopper-zink, NG-GL4ES,
#     spirv-cross, LWJGL natives        : diekstrak dari AAR app/libs (armeabi-v7a). Gradle tidak
#                                         memasukkan isi AAR lokal ke APK, jadi harus disalin sendiri.
# Yang TIDAK ada untuk 32-bit (hulu pun tidak menyediakan): Turnip (libvulkan_freedreno).
set -euo pipefail

# Revisi hulu ZalithLauncher2 yang kodenya paling dekat dengan pustaka 64-bit di repo ini
# (8 Sep 2026, sebelum fitur narator 9 Sep). Jangan diganti tanpa membandingkan ekspor JNI.
UPSTREAM_URL="https://github.com/ZalithLauncher/ZalithLauncher2.git"
UPSTREAM_SHA="1aea3976cfcf06963881f1b5389f076a3916eeaf"
# Commit awal repo ini (berisi libmobileglues armeabi-v7a) dan induk dari commit yang menghapus bin-arm.
MOBILEGLUES_COMMIT="50e49fae5e08216dc28134c1d45fb4793d6970db"
RUNTIME_COMMIT="aac1cbd480550abeef3938c63e187aec2cebf990^"
NDK_VERSION="25.2.9519653"

# Bila skrip berhenti di tengah, tahap terakhir yang dijalankan muncul sebagai peringatan di halaman
# ringkasan run (langkah ini continue-on-error, jadi tanpa ini kegagalannya mudah tidak terlihat).
STAGE="persiapan"
trap 'rc=$?; if [ "$rc" -ne 0 ]; then echo "::warning title=APK 32-bit gagal::Berhenti di tahap: ${STAGE} (kode ${rc}). APK 64-bit tidak terpengaruh. Rincian ada di log langkah Bangun APK 32-bit."; fi' EXIT

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"
: "${VERSION_NAME:?VERSION_NAME belum diatur}"
TMP="${RUNNER_TEMP:-/tmp}"
WORK="$TMP/amber-arm32"
UP="$TMP/zalith-upstream"
OUT="$ROOT/dist"
APK_NAME="AmberLauncher-${VERSION_NAME}-32bit.apk"

STAGE="1/6 pasang NDK"
echo "== 1/6 Pasang NDK $NDK_VERSION =="
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
if [ ! -d "$SDK_ROOT/ndk/$NDK_VERSION" ]; then
  # "yes" selalu berakhir dengan kode 141 (SIGPIPE) begitu sdkmanager selesai, dan pipefail akan
  # menghentikan skrip. Hasil sebenarnya dicek lewat "test -d" di bawah.
  yes | "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" "ndk;$NDK_VERSION" > /dev/null || true
fi
test -d "$SDK_ROOT/ndk/$NDK_VERSION"
echo "NDK terpasang: $(ls "$SDK_ROOT/ndk" | tr '\n' ' ')"

STAGE="2/6 ambil kode hulu"
echo "== 2/6 Ambil kode dan pustaka 32-bit dari proyek hulu ($UPSTREAM_SHA) =="
rm -rf "$UP"
git init -q "$UP"
git -C "$UP" fetch -q --depth 1 "$UPSTREAM_URL" "$UPSTREAM_SHA"
git -C "$UP" checkout -q FETCH_HEAD
test "$(git -C "$UP" rev-parse HEAD)" = "$UPSTREAM_SHA"
UPMAIN="$UP/ZalithLauncher/src/main"

STAGE="3/6 salin repo"
echo "== 3/6 Siapkan salinan kerja =="
rm -rf "$WORK"
mkdir -p "$WORK"
tar -C "$ROOT" --exclude=.git --exclude=dist --exclude=build --exclude=.gradle --exclude=.kotlin -cf - . | tar -C "$WORK" -xf -
cd "$WORK"
APP="$WORK/app/src/main"

# Kode C untuk pustaka jembatan
rm -rf "$APP/jni"
cp -r "$UPMAIN/jni" "$APP/jni"

# Buang bagian 64-bit dari salinan (tidak ikut APK 32-bit)
rm -rf "$APP/jniLibs/arm64-v8a"
rm -f "$APP"/assets/runtimes/*/bin-arm64.tar.xz

# Pustaka grafis 32-bit dari hulu
mkdir -p "$APP/jniLibs/armeabi-v7a"
for lib in libEGL_angle libGLESv2_angle libOSMesa_8 libgl4es_114 libjnidispatch libshaderconv libunpack200; do
  cp "$UPMAIN/jniLibs/armeabi-v7a/$lib.so" "$APP/jniLibs/armeabi-v7a/$lib.so"
done

STAGE="4/6 ambil MobileGlues, runtime Java, LWJGL"
echo "== 4/6 Ambil MobileGlues dan runtime Java 32-bit dari riwayat repo =="
for lib in libmobileglues libmobileglues_info_getter; do
  git -C "$ROOT" show "$MOBILEGLUES_COMMIT:app/src/main/jniLibs/armeabi-v7a/$lib.so" > "$APP/jniLibs/armeabi-v7a/$lib.so"
done
# SDL (libSDL3/libSDL2), OpenAL (suara), NG-GL4ES, kopper-zink, spirv-cross. Di APK 64-bit pustaka ini
# berada di jniLibs/arm64-v8a; AAR di app/libs hanya penyimpanan, tidak ikut dikemas Gradle. Tanpa langkah
# ini APK 32-bit tidak punya libopenal.so (game tanpa suara) dan libSDL3.so ("library libSDL3.so not found").
# "-n": jangan menimpa pustaka hulu yang sudah disalin di atas. unzip berhenti dengan kode error bila
# tidak ada pustaka armeabi-v7a di AAR-nya, jadi AAR yang kosong tidak lolos diam-diam.
for aar in SDL-release openal-soft-release NG-GL4ES-release kopper-zink-release spirv-cross-natives; do
  test -f "$WORK/app/libs/$aar.aar"
  unzip -n -j -q "$WORK/app/libs/$aar.aar" 'jni/armeabi-v7a/*.so' -d "$APP/jniLibs/armeabi-v7a"
done
echo "Pustaka 32-bit dari AAR: $(ls "$APP/jniLibs/armeabi-v7a" | grep -cE '^lib(SDL2|SDL3|openal|ng_gl4es|spirv-cross-c-shared|EGL_mesa|cutils|glapi|glxshim|zink_dri)\.so$') berkas"
for v in 8 17 21 25; do
  git -C "$ROOT" show "$RUNTIME_COMMIT:app/src/main/assets/runtimes/jre-$v/bin-arm.tar.xz" > "$APP/assets/runtimes/jre-$v/bin-arm.tar.xz"
done

# Pustaka native LWJGL: salinan datar di assets adalah versi arm64, ganti dengan armeabi-v7a dari AAR
for aar in "$WORK"/app/libs/lwjgl-*-natives-release.aar; do
  v="$(basename "$aar" | sed -E 's/^lwjgl-([0-9]+\.[0-9]+\.[0-9]+)-natives-release\.aar$/\1/')"
  dest="$APP/assets/app_runtime/lwjgl/$v/natives"
  mkdir -p "$dest"
  unzip -o -j -q "$aar" "assets/app_runtime/lwjgl/$v/natives/armeabi-v7a/*.so" -d "$dest"
  echo "LWJGL $v: $(ls "$dest" | wc -l) pustaka 32-bit"
done

STAGE="5/6 Gradle + NDK (cari baris diawali 'e:' atau 'ndk-build' di log)"
echo "== 5/6 Bangun APK (Gradle + NDK) =="
chmod +x gradlew
export AMBER_VERSION_NAME="$VERSION_NAME"
AMBER_ABI=arm32 ./gradlew assembleRelease --stacktrace

APK="$(find app/build/outputs/apk/release -name '*.apk' | head -n 1)"
test -n "$APK"

STAGE="6/6 periksa isi APK"
echo "== 6/6 Periksa isi APK =="
# Daftar isi disimpan dulu: "unzip | grep -q" di bawah pipefail bisa salah dianggap gagal (SIGPIPE).
APK_LIST="$(unzip -l "$APK")"
printf '%s\n' "$APK_LIST" | grep -E 'lib/[^/]+/lib[^/]+\.so$' | awk '{print $4, $1}' | sed -E 's#^lib/##' | sort || true
if grep -qE 'lib/(arm64-v8a|x86|x86_64)/' <<< "$APK_LIST"; then
  echo "::error::APK 32-bit berisi pustaka arsitektur lain."
  exit 1
fi
for lib in libpojavexec libexithook liblinkerhook libdriver_helper libjnidispatch libunpack200 libmobileglues libbytehook libSDL3 libSDL2 libopenal libng_gl4es libzink_dri libspirv-cross-c-shared; do
  if ! grep -q "lib/armeabi-v7a/$lib.so" <<< "$APK_LIST"; then
    echo "::error::$lib.so (armeabi-v7a) tidak ada di APK 32-bit."
    exit 1
  fi
done

mkdir -p "$OUT"
cp "$APK" "$OUT/$APK_NAME"
ls -lh "$OUT/$APK_NAME"
APKSIGNER="$(ls -d "$SDK_ROOT"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -n 1 || true)"
if [ -n "$APKSIGNER" ]; then
  "$APKSIGNER" verify --print-certs "$OUT/$APK_NAME" | grep -i "SHA-256" || true
fi
echo "APK 32-bit siap: $APK_NAME"
