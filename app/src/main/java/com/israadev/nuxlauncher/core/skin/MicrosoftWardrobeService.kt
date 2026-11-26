package com.israadev.nuxlauncher.core.skin

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.israadev.nuxlauncher.core.account.AccountManager
import com.israadev.nuxlauncher.core.account.microsoft.MicrosoftAuthService
import com.israadev.nuxlauncher.core.models.UserAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import com.israadev.nuxlauncher.core.network.HttpClients
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class MicrosoftCape(
    val id: String,
    var state: String = "INACTIVE", // "ACTIVE" or "INACTIVE"
    val url: String,
    val alias: String? = null
)

data class MicrosoftProfileResponse(
    val id: String,
    val name: String,
    val skins: List<MicrosoftSkinItem> = emptyList(),
    val capes: List<MicrosoftCape> = emptyList()
)

data class MicrosoftSkinItem(
    val id: String,
    val state: String = "INACTIVE",
    val url: String,
    val variant: String = "CLASSIC" // "CLASSIC" or "SLIM"
)

/** Jawaban Session Server soal jubah: ada ([Found]), memang tidak ada ([None]), atau tidak bisa dipastikan ([Failed]). */
sealed class SessionCape {
    class Found(val url: String) : SessionCape()
    object None : SessionCape()
    object Failed : SessionCape()
}

/**
 * Jawaban Session Server soal tekstur akun: skin (dengan model lengannya) dan jubah. [Found] bisa
 * berisi skin atau jubah yang null (memang tidak ada), [None] = profil tanpa tekstur, [Failed] = belum
 * bisa dipastikan (jaringan putus atau kena batas laju).
 */
sealed class SessionTextures {
    class Found(val skinUrl: String?, val slim: Boolean, val capeUrl: String?) : SessionTextures()
    object None : SessionTextures()
    object Failed : SessionTextures()
}

object MicrosoftWardrobeService {
    private const val MINECRAFT_SERVICES_URL = "https://api.minecraftservices.com"
    private const val MOJANG_SESSION_URL = "https://sessionserver.mojang.com"

    // Session Server membatasi laju permintaan per pemain (HTTP 429). Beranda, Akun, dan Wardrobe
    // bertanya soal akun yang sama, jadi satu jawaban dipakai bersama dan permintaan diserialkan.
    private const val SESSION_TTL_MS = 5L * 60 * 1000 // jawaban yang pasti dipakai ulang sampai 5 menit
    private const val SESSION_FAIL_MIN_MS = 8_000L // gagal: jangan langsung diulang
    private const val SESSION_FAIL_MAX_MS = 120_000L // batas atas penundaan dari Retry-After
    private class SessionEntry(val until: Long, val result: SessionTextures)
    private val sessionCache = ConcurrentHashMap<String, SessionEntry>()
    private val sessionLock = Mutex()

    private val httpClient = HttpClients.base.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // Pencarian tekstur ikut menahan layar yang menunggunya, jadi batas waktunya lebih pendek
    private val sessionClient = httpClient.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Nama tampilan ramah untuk tiap alias jubah resmi Minecraft (seperti pada ZalithLauncher)
     */
    fun getCapeDisplayName(alias: String?): String {
        return when (alias) {
            "Migrator" -> "Migrator Cape"
            "Vanilla" -> "Vanilla Cape"
            "Cherry Blossom" -> "Cherry Blossom Cape"
            "15th Anniversary" -> "15th Anniversary Cape"
            "Purple Heart" -> "Purple Heart Cape (Twitch)"
            "Follower's" -> "Follower's Cape (TikTok)"
            "MCC 15th Year" -> "MCC 15th Year Cape"
            "Minecon2011" -> "Minecon 2011 Cape"
            "Minecon2012" -> "Minecon 2012 Cape"
            "Minecon2013" -> "Minecon 2013 Cape"
            "Minecon2015" -> "Minecon 2015 Cape"
            "Minecon2016" -> "Minecon 2016 Cape"
            "Minecraft Experience" -> "Minecraft Experience Cape"
            "Mojang Office" -> "Mojang Office Cape"
            "Home" -> "Home Cape"
            "Menace" -> "Menace Cape"
            "Yearn" -> "Yearn Cape"
            "Common" -> "Common Cape"
            null, "" -> "Jubah Resmi Minecraft"
            else -> "$alias Cape"
        }
    }

    private fun httpsOrNull(raw: String?): String? {
        val url = raw?.trim().orEmpty()
        if (url.isEmpty()) return null
        return if (url.startsWith("http://")) "https://" + url.removePrefix("http://") else url
    }

    private fun parseSessionTextures(body: String): SessionTextures {
        val json = JsonParser.parseString(body).asJsonObject
        val props = json.getAsJsonArray("properties") ?: return SessionTextures.None
        for (p in props) {
            val pObj = p.asJsonObject
            if (pObj.get("name")?.asString != "textures") continue
            val base64Val = pObj.get("value")?.asString ?: continue
            val decodedBytes = Base64.decode(base64Val, Base64.DEFAULT)
            val decodedJson = JsonParser.parseString(String(decodedBytes, Charsets.UTF_8)).asJsonObject
            val textures = decodedJson.getAsJsonObject("textures") ?: continue
            val skin = textures.getAsJsonObject("SKIN")
            val cape = textures.getAsJsonObject("CAPE")
            return SessionTextures.Found(
                skinUrl = httpsOrNull(skin?.get("url")?.asString),
                slim = skin?.getAsJsonObject("metadata")?.get("model")?.asString.equals("slim", ignoreCase = true),
                capeUrl = httpsOrNull(cape?.get("url")?.asString)
            )
        }
        return SessionTextures.None
    }

    /** Satu permintaan ke Session Server. Angka kedua = berapa lama jawabannya boleh dipakai ulang. */
    private fun fetchSessionTextures(cleanUuid: String): Pair<SessionTextures, Long> {
        return try {
            val req = Request.Builder()
                .url("$MOJANG_SESSION_URL/session/minecraft/profile/$cleanUuid")
                .get()
                .build()

            sessionClient.newCall(req).execute().use { resp ->
                if (resp.code == 204 || resp.code == 404) return Pair(SessionTextures.None, SESSION_TTL_MS)
                if (!resp.isSuccessful) {
                    // 429 (kena batas laju) menyertakan Retry-After bila server mau menyebut jedanya
                    val waitMs = (resp.header("Retry-After")?.trim()?.toLongOrNull() ?: 0L) * 1000L
                    return Pair(SessionTextures.Failed, waitMs.coerceIn(SESSION_FAIL_MIN_MS, SESSION_FAIL_MAX_MS))
                }
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return Pair(SessionTextures.Failed, SESSION_FAIL_MIN_MS)
                Pair(parseSessionTextures(body), SESSION_TTL_MS)
            }
        } catch (_: Exception) {
            Pair(SessionTextures.Failed, SESSION_FAIL_MIN_MS)
        }
    }

    /**
     * Mengambil skin, model lengan, dan jubah aktif langsung dari Session Server resmi Mojang tanpa perlu
     * token OAuth. Sama seperti yang dibaca game saat dijalankan, jadi hasilnya yang akan terlihat di dalam game.
     * Jawaban dipakai bersama (Beranda, Akun, Wardrobe) dan gagal tidak langsung diulang, supaya tidak
     * menabrak batas laju Mojang.
     */
    suspend fun lookupSessionTextures(uuid: String): SessionTextures {
        val key = uuid.replace("-", "").lowercase()
        if (key.isEmpty()) return SessionTextures.None
        return sessionLock.withLock {
            val now = System.currentTimeMillis()
            val hit = sessionCache[key]
            if (hit != null && now < hit.until) {
                hit.result
            } else {
                val (result, reuseMs) = withContext(Dispatchers.IO) { fetchSessionTextures(key) }
                sessionCache[key] = SessionEntry(System.currentTimeMillis() + reuseMs, result)
                result
            }
        }
    }

    /** Lupakan jawaban tersimpan, dipakai setelah skin atau jubah diganti dari aplikasi ini. */
    fun invalidateSessionTextures(uuid: String) {
        sessionCache.remove(uuid.replace("-", "").lowercase())
    }

    suspend fun lookupSessionCape(uuid: String): SessionCape = when (val t = lookupSessionTextures(uuid)) {
        is SessionTextures.Found -> t.capeUrl?.let { SessionCape.Found(it) } ?: SessionCape.None
        is SessionTextures.None -> SessionCape.None
        is SessionTextures.Failed -> SessionCape.Failed
    }

    suspend fun getSessionActiveCape(uuid: String): String? =
        (lookupSessionCape(uuid) as? SessionCape.Found)?.url

    /**
     * Mengambil profil lengkap pemain dari api.minecraftservices.com.
     * Jika token kedaluwarsa (HTTP 401), secara otomatis memperbarui token via refresh_token,
     * menyimpan akun yang diperbarui ke AccountManager, lalu mencoba kembali.
     * Juga menggabungkan informasi dari Session Server jika diperlukan agar cape aktif selalu terdeteksi.
     */
    suspend fun getProfile(
        context: Context,
        account: UserAccount
    ): Result<Pair<UserAccount, MicrosoftProfileResponse>> = withContext(Dispatchers.IO) {
        var currentAcc = account

        // 1. Coba fetch dengan accessToken saat ini
        var firstResult = fetchProfileWithToken(currentAcc.safeAccessToken)

        // 2. Jika 401 Unauthorized (token kedaluwarsa) dan ada refresh_token, lakukan auto-refresh
        if (firstResult.isFailure && currentAcc.safeRefreshToken.isNotBlank() && currentAcc.safeRefreshToken != "0") {
            val refreshResult = MicrosoftAuthService.refreshMicrosoftAccount(currentAcc)
            if (refreshResult.isSuccess) {
                currentAcc = refreshResult.getOrThrow()
                AccountManager.updateAccount(context, currentAcc)
                firstResult = fetchProfileWithToken(currentAcc.safeAccessToken)
            }
        }

        // 3. Ambil juga tekstur jubah aktif dari Mojang Session Server sebagai jaminan/fallback
        val sessionCapeUrl = getSessionActiveCape(currentAcc.uuid)

        if (firstResult.isSuccess) {
            val baseProf = firstResult.getOrThrow()
            val capesList = baseProf.capes.toMutableList()

            // Sinkronkan status ACTIVE dengan session cape. Hanya bila api.minecraftservices.com tidak menandai
            // jubah aktif: Session Server bisa tertinggal beberapa menit, jadi ia tidak boleh menimpa jubah
            // yang baru diganti (dulu dua jubah bisa sama-sama "aktif" dan yang terpilih bisa yang lama).
            if (sessionCapeUrl != null && capesList.none { it.state.equals("ACTIVE", ignoreCase = true) }) {
                var foundMatch = false
                for (cape in capesList) {
                    val safeCapeUrl = if (cape.url.startsWith("http://")) cape.url.replace("http://", "https://") else cape.url
                    if (safeCapeUrl == sessionCapeUrl || cape.url == sessionCapeUrl) {
                        cape.state = "ACTIVE"
                        foundMatch = true
                    }
                }
                // Jika user punya cape aktif di session tapi belum tercantum di daftar capes, tambahkan
                if (!foundMatch) {
                    capesList.add(
                        MicrosoftCape(
                            id = "mojang_active_cape",
                            state = "ACTIVE",
                            url = sessionCapeUrl,
                            alias = "Jubah Aktif Minecraft"
                        )
                    )
                }
            }

            val finalProf = baseProf.copy(capes = capesList)
            return@withContext Result.success(Pair(currentAcc, finalProf))
        }

        // 4. Jika api.minecraftservices.com gagal (misal koneksi / limit), namun session server punya cape aktif
        if (sessionCapeUrl != null) {
            val fallbackProf = MicrosoftProfileResponse(
                id = currentAcc.uuid,
                name = currentAcc.username,
                capes = listOf(
                    MicrosoftCape(
                        id = "mojang_active_cape",
                        state = "ACTIVE",
                        url = sessionCapeUrl,
                        alias = "Jubah Aktif Minecraft"
                    )
                )
            )
            return@withContext Result.success(Pair(currentAcc, fallbackProf))
        }

        // Jika semua gagal, teruskan exception
        Result.failure(firstResult.exceptionOrNull() ?: IOException("Gagal memuat jubah Minecraft."))
    }

    private fun fetchProfileWithToken(accessToken: String): Result<MicrosoftProfileResponse> {
        return try {
            val req = Request.Builder()
                .url("$MINECRAFT_SERVICES_URL/minecraft/profile")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return Result.failure(IOException("HTTP ${resp.code}: ${resp.message}"))
                }
                val body = resp.body?.string() ?: return Result.failure(IOException("Respon profil kosong"))
                val profile = gson.fromJson(body, MicrosoftProfileResponse::class.java)
                Result.success(profile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mengubah jubah aktif di Minecraft.net dengan auto-refresh token jika kedaluwarsa
     */
    suspend fun changeCape(
        context: Context,
        account: UserAccount,
        capeId: String?
    ): Result<UserAccount> = withContext(Dispatchers.IO) {
        var currentAcc = account

        var res = doChangeCapeCall(currentAcc.safeAccessToken, capeId)
        if (res.isFailure && currentAcc.safeRefreshToken.isNotBlank() && currentAcc.safeRefreshToken != "0") {
            val refreshResult = MicrosoftAuthService.refreshMicrosoftAccount(currentAcc)
            if (refreshResult.isSuccess) {
                currentAcc = refreshResult.getOrThrow()
                AccountManager.updateAccount(context, currentAcc)
                res = doChangeCapeCall(currentAcc.safeAccessToken, capeId)
            }
        }

        if (res.isSuccess) {
            invalidateSessionTextures(currentAcc.uuid)
            Result.success(currentAcc)
        } else {
            Result.failure(res.exceptionOrNull() ?: IOException("Gagal mengubah jubah."))
        }
    }

    private fun doChangeCapeCall(accessToken: String, capeId: String?): Result<Unit> {
        return try {
            val url = "$MINECRAFT_SERVICES_URL/minecraft/profile/capes/active"
            val req = if (capeId.isNullOrBlank() || capeId == "mojang_active_cape") {
                Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $accessToken")
                    .delete()
                    .build()
            } else {
                val jsonBody = JsonObject().apply {
                    addProperty("capeId", capeId)
                }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

                Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $accessToken")
                    .put(jsonBody)
                    .build()
            }

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 200 && resp.code != 204) {
                    return Result.failure(IOException("HTTP ${resp.code}: ${resp.message}"))
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mengunggah skin baru ke Minecraft.net dengan auto-refresh token jika kedaluwarsa
     */
    suspend fun uploadSkin(
        context: Context,
        account: UserAccount,
        file: File,
        isSlim: Boolean
    ): Result<UserAccount> = withContext(Dispatchers.IO) {
        var currentAcc = account

        var res = doUploadSkinCall(currentAcc.safeAccessToken, file, isSlim)
        if (res.isFailure && currentAcc.safeRefreshToken.isNotBlank() && currentAcc.safeRefreshToken != "0") {
            val refreshResult = MicrosoftAuthService.refreshMicrosoftAccount(currentAcc)
            if (refreshResult.isSuccess) {
                currentAcc = refreshResult.getOrThrow()
                AccountManager.updateAccount(context, currentAcc)
                res = doUploadSkinCall(currentAcc.safeAccessToken, file, isSlim)
            }
        }

        if (res.isSuccess) {
            invalidateSessionTextures(currentAcc.uuid)
            Result.success(currentAcc)
        } else {
            Result.failure(res.exceptionOrNull() ?: IOException("Gagal mengunggah skin."))
        }
    }

    private fun doUploadSkinCall(accessToken: String, file: File, isSlim: Boolean): Result<Unit> {
        return try {
            val variant = if (isSlim) "slim" else "classic"
            val fileBody = file.readBytes().toRequestBody("image/png".toMediaType())
            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("variant", variant)
                .addFormDataPart("file", file.name, fileBody)
                .build()

            val req = Request.Builder()
                .url("$MINECRAFT_SERVICES_URL/minecraft/profile/skins")
                .header("Authorization", "Bearer $accessToken")
                .post(multipartBody)
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 200 && resp.code != 204) {
                    return Result.failure(IOException("HTTP ${resp.code}: ${resp.message}"))
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
