// Jembatan JNI antara Kotlin (discord/DiscordPresence.kt) dan Discord Social SDK.
//
// Hanya memakai jalur Rich Presence TANPA login: Client tidak pernah memanggil Connect, jadi
// presence dikirim lewat aplikasi Discord yang terpasang di HP ini. Tidak ada token atau password.
//
// Aturan pemakaian: SEMUA fungsi di sini dipanggil dari satu thread yang sama (HandlerThread di Kotlin),
// termasuk nativeTick yang menjalankan callback SDK.

#define DISCORDPP_IMPLEMENTATION  // hanya di SATU file
#include "discordpp.h"

#include <android/log.h>
#include <jni.h>

#include <memory>
#include <mutex>
#include <string>

namespace {

constexpr const char* kTag = "AmberDiscord";
// Nama yang tampil sesudah "Playing" di profil Discord. Tanpa login, SDK tidak bisa mencarinya sendiri.
constexpr const char* kActivityName = "AmberLauncher";
constexpr size_t kMaxLogBytes = 8 * 1024;

std::shared_ptr<discordpp::Client> g_client;

// Hasil terakhir dan log SDK ditulis dari callback (bisa dari thread SDK), jadi dikunci.
std::mutex g_mutex;
std::string g_result;  // kosong = belum ada hasil baru
std::string g_log;

void AppendLog(const std::string& line) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_log.size() + line.size() + 1 > kMaxLogBytes) return;
    g_log += line;
    g_log += '\n';
}

void SetResult(const std::string& text) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_result = text;
}

std::string TakeString(std::string& source) {
    std::lock_guard<std::mutex> lock(g_mutex);
    std::string out;
    out.swap(source);
    return out;
}

std::string BytesToString(JNIEnv* env, jbyteArray array) {
    if (array == nullptr) return std::string();
    const jsize length = env->GetArrayLength(array);
    std::string out(static_cast<size_t>(length), '\0');
    if (length > 0) {
        env->GetByteArrayRegion(array, 0, length, reinterpret_cast<jbyte*>(out.data()));
    }
    return out;
}

jbyteArray StringToBytes(JNIEnv* env, const std::string& text) {
    const jsize length = static_cast<jsize>(text.size());
    jbyteArray array = env->NewByteArray(length);
    if (array != nullptr && length > 0) {
        env->SetByteArrayRegion(array, 0, length, reinterpret_cast<const jbyte*>(text.data()));
    }
    return array;
}

const char* SeverityName(discordpp::LoggingSeverity severity) {
    switch (severity) {
        case discordpp::LoggingSeverity::Verbose: return "V";
        case discordpp::LoggingSeverity::Info: return "I";
        case discordpp::LoggingSeverity::Warning: return "W";
        case discordpp::LoggingSeverity::Error: return "E";
        default: return "?";
    }
}

}  // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_israadev_nuxlauncher_discord_NativeDiscord_nativeInit(JNIEnv*, jobject, jlong appId) {
    try {
        if (g_client) return JNI_TRUE;
        auto client = std::make_shared<discordpp::Client>();
        // Hanya peringatan dan galat yang dicatat: log Info SDK ramai dan tidak berguna bagi pemain.
        client->AddLogCallback(
            [](std::string message, discordpp::LoggingSeverity severity) {
                __android_log_print(ANDROID_LOG_INFO, kTag, "[%s] %s", SeverityName(severity), message.c_str());
                AppendLog(std::string("[") + SeverityName(severity) + "] " + message);
            },
            discordpp::LoggingSeverity::Warning);
        client->SetApplicationId(static_cast<uint64_t>(appId));
        g_client = std::move(client);
        return JNI_TRUE;
    } catch (const std::exception& e) {
        SetResult(std::string("init gagal: ") + e.what());
    } catch (...) {
        SetResult("init gagal: galat tidak dikenal");
    }
    return JNI_FALSE;
}

// details/state: teks UTF-8 (kosong = tidak diisi). startMs <= 0 = tanpa penghitung waktu.
JNIEXPORT jboolean JNICALL
Java_com_israadev_nuxlauncher_discord_NativeDiscord_nativeUpdate(JNIEnv* env, jobject, jbyteArray details,
                                                                 jbyteArray state, jlong startMs) {
    if (!g_client) return JNI_FALSE;
    try {
        const std::string detailsText = BytesToString(env, details);
        const std::string stateText = BytesToString(env, state);

        discordpp::Activity activity;
        activity.SetType(discordpp::ActivityTypes::Playing);
        activity.SetName(kActivityName);
        if (!detailsText.empty()) activity.SetDetails(detailsText);
        if (!stateText.empty()) activity.SetState(stateText);

        if (startMs > 0) {
            discordpp::ActivityTimestamps timestamps;
            timestamps.SetStart(static_cast<uint64_t>(startMs));
            activity.SetTimestamps(timestamps);
        }

        g_client->UpdateRichPresence(std::move(activity), [](discordpp::ClientResult result) {
            if (result.Successful()) {
                SetResult("ok");
            } else {
                SetResult("gagal: " + result.Error());
            }
        });
        return JNI_TRUE;
    } catch (const std::exception& e) {
        SetResult(std::string("update gagal: ") + e.what());
    } catch (...) {
        SetResult("update gagal: galat tidak dikenal");
    }
    return JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_israadev_nuxlauncher_discord_NativeDiscord_nativeClear(JNIEnv*, jobject) {
    try {
        if (g_client) g_client->ClearRichPresence();
    } catch (...) {
    }
}

// Menjalankan callback SDK. Dipanggil berkala dari thread yang sama.
JNIEXPORT void JNICALL
Java_com_israadev_nuxlauncher_discord_NativeDiscord_nativeTick(JNIEnv*, jobject) {
    try {
        discordpp::RunCallbacks();
    } catch (...) {
    }
}

JNIEXPORT void JNICALL
Java_com_israadev_nuxlauncher_discord_NativeDiscord_nativeShutdown(JNIEnv*, jobject) {
    try {
        if (g_client) {
            g_client->ClearRichPresence();
            discordpp::RunCallbacks();
            g_client.reset();
        }
    } catch (...) {
        g_client.reset();
    }
}

JNIEXPORT jbyteArray JNICALL
Java_com_israadev_nuxlauncher_discord_NativeDiscord_nativeTakeResult(JNIEnv* env, jobject) {
    return StringToBytes(env, TakeString(g_result));
}

JNIEXPORT jbyteArray JNICALL
Java_com_israadev_nuxlauncher_discord_NativeDiscord_nativeTakeLog(JNIEnv* env, jobject) {
    return StringToBytes(env, TakeString(g_log));
}

}  // extern "C"
