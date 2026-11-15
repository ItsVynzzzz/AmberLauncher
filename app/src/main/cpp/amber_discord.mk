# Jembatan JNI ke Discord Social SDK untuk build 32-bit (ndk-build). scripts/build-arm32.sh menyalin berkas ini
# ke jni/amber_discord/Android.mk dan menambahkan satu baris "include" ke jni/Android.mk, HANYA bila SDK
# ada di app/libs. Sumbernya sama dengan build 64-bit (cpp/discord_bridge.cpp).
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := amber_discord
LOCAL_SRC_FILES := ../../cpp/discord_bridge.cpp
LOCAL_CPPFLAGS += -std=c++20
LOCAL_CPP_FEATURES := exceptions rtti
LOCAL_LDLIBS := -llog
LOCAL_SHARED_LIBRARIES := discord_partner_sdk
include $(BUILD_SHARED_LIBRARY)

# Paket prefab dari discord_partner_sdk.aar (header dan libdiscord_partner_sdk.so).
$(call import-module,prefab/discord_partner_sdk)
