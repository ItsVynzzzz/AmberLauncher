package com.israadev.nuxlauncher.core.account.elyby

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.israadev.nuxlauncher.core.models.UserAccount
import com.israadev.nuxlauncher.core.network.HttpClients
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Akun Ely.by memakai verifikasi dua langkah (2FA). Pemanggil harus meminta kode TOTP dari
 * pengguna lalu mengulang login dengan kode itu.
 */
class ElyByTwoFactorRequiredException :
    Exception("Akun Ely.by ini memakai verifikasi dua langkah (2FA). Masukkan kode dari aplikasi autentikator.")

/**
 * Login Ely.by lewat autentikasi langsung (email/username + password) ke authserver.ely.by,
 * protokol Yggdrasil yang didokumentasikan Ely.by dan dipakai launcher lain (Prism, HMCL, dll).
 *
 * Alur OAuth lama (Device Code dengan client_id "ely") dibuang: "ely" hanya contoh di dokumentasi
 * Ely.by, bukan aplikasi terdaftar, sehingga server selalu menjawab invalid_client. OAuth Ely.by yang
 * resmi butuh aplikasi terdaftar sendiri (client_id + secret) dan alur authorization code, bukan Device Code.
 *
 * Password tidak pernah disimpan: hanya accessToken dan clientToken (di kolom refreshToken).
 */
object ElyByAuthService {
    const val AUTHLIB_INJECTOR_URL = "https://authserver.ely.by/api/authlib-injector"
    private const val AUTH_SERVER = "https://authserver.ely.by"
    private const val RELOGIN_MESSAGE = "Sesi Ely.by sudah tidak berlaku. Hapus akun ini lalu masuk lagi."
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val httpClient = HttpClients.base.newBuilder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private class AuthReply(val code: Int, val success: Boolean, val json: JsonObject?, val body: String)

    private fun JsonObject.str(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.obj(name: String): JsonObject? =
        get(name)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun postJson(path: String, payload: JsonObject): AuthReply {
        val request = Request.Builder()
            .url("$AUTH_SERVER$path")
            .header("Accept", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            val json = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
            return AuthReply(response.code, response.isSuccessful, json, body)
        }
    }

    /**
     * Masuk dengan email atau username Ely.by dan password. Bila akun memakai 2FA, hasilnya gagal dengan
     * [ElyByTwoFactorRequiredException]; panggil lagi dengan [twoFactorCode] terisi (kode 6 digit dari aplikasi
     * autentikator). Sesuai dokumentasi Ely.by, kode ditempel ke password dalam bentuk `password:kode`.
     */
    suspend fun loginWithCredentials(
        usernameOrEmail: String,
        password: String,
        twoFactorCode: String? = null
    ): Result<UserAccount> = withContext(Dispatchers.IO) {
        try {
            val login = usernameOrEmail.trim()
            val code = twoFactorCode?.trim().orEmpty()
            val clientToken = UUID.randomUUID().toString().replace("-", "")
            val payload = JsonObject().apply {
                add("agent", JsonObject().apply {
                    addProperty("name", "Minecraft")
                    addProperty("version", 1)
                })
                addProperty("username", login)
                addProperty("password", if (code.isEmpty()) password else "$password:$code")
                addProperty("clientToken", clientToken)
                addProperty("requestUser", true)
            }

            val reply = postJson("/auth/authenticate", payload)
            val json = reply.json
            val errMsg = json?.str("errorMessage")
            if (errMsg != null) {
                return@withContext Result.failure(
                    when {
                        errMsg.contains("two factor", ignoreCase = true) -> ElyByTwoFactorRequiredException()
                        errMsg.contains("Invalid credentials", ignoreCase = true) -> Exception(
                            if (code.isEmpty()) "Email/username atau password Ely.by salah."
                            else "Email/username, password, atau kode 2FA Ely.by salah."
                        )
                        else -> Exception("Ely.by: $errMsg")
                    }
                )
            }
            if (!reply.success || json == null) {
                return@withContext Result.failure(
                    Exception("Gagal login ke Ely.by (HTTP ${reply.code}): ${reply.body.take(200)}")
                )
            }

            val accessToken = json.str("accessToken").orEmpty()
            if (accessToken.isBlank()) {
                return@withContext Result.failure(Exception("Respon Ely.by tidak berisi token akses."))
            }
            val resClientToken = json.str("clientToken") ?: clientToken
            val profile = json.obj("selectedProfile")?.takeIf { it.has("id") }
                ?: json.get("availableProfiles")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.firstOrNull { it.isJsonObject && it.asJsonObject.has("id") }?.asJsonObject
                ?: return@withContext Result.failure(Exception("Profil Ely.by tidak ditemukan dalam respon."))

            val profileId = (profile.str("id") ?: "").replace("-", "")
            if (profileId.isBlank()) {
                return@withContext Result.failure(Exception("Profil Ely.by tidak ditemukan dalam respon."))
            }
            val profileName = profile.str("name") ?: login

            Result.success(
                UserAccount(
                    id = "ely_$profileId",
                    username = profileName,
                    uuid = profileId,
                    accessToken = accessToken,
                    refreshToken = resClientToken,
                    isOffline = false,
                    accountType = "elyby",
                    authServerUrl = AUTHLIB_INJECTOR_URL,
                    email = if (login.contains("@")) login else "",
                    skinUrl = "https://skinsystem.ely.by/skins/$profileName.png",
                    tokenIssuedAt = System.currentTimeMillis()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Result.failure(Exception("Tidak bisa terhubung ke Ely.by. Periksa koneksi internet."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Memeriksa token akun Ely.by dan memperbaruinya bila sudah tidak sah, tanpa menanyakan password lagi.
     * Gagal bila token tidak bisa diperbarui; pengguna perlu masuk ulang.
     */
    suspend fun refreshElyByAccount(account: UserAccount): Result<UserAccount> = withContext(Dispatchers.IO) {
        try {
            val accessToken = account.safeAccessToken
            val clientToken = account.safeRefreshToken
            if (accessToken == "0" || clientToken == "0") {
                return@withContext Result.failure(Exception(RELOGIN_MESSAGE))
            }

            val validate = postJson("/auth/validate", JsonObject().apply {
                addProperty("accessToken", accessToken)
            })
            if (validate.success) {
                return@withContext Result.success(account.copy(tokenIssuedAt = System.currentTimeMillis()))
            }

            val refresh = postJson("/auth/refresh", JsonObject().apply {
                addProperty("accessToken", accessToken)
                addProperty("clientToken", clientToken)
            })
            val newToken = refresh.json?.str("accessToken")
            if (!refresh.success || newToken.isNullOrBlank()) {
                return@withContext Result.failure(Exception(RELOGIN_MESSAGE))
            }
            Result.success(
                account.copy(
                    accessToken = newToken,
                    refreshToken = refresh.json?.str("clientToken") ?: clientToken,
                    tokenIssuedAt = System.currentTimeMillis()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Result.failure(Exception("Tidak bisa terhubung ke Ely.by. Periksa koneksi internet."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Resolves authentic 32-character Ely.by Yggdrasil UUID for username
     */
    fun fetchRealPlayerUuid(username: String): String {
        return try {
            val uuidReq = Request.Builder()
                .url("https://authserver.ely.by/api/users/profiles/minecraft/$username")
                .get()
                .build()
            httpClient.newCall(uuidReq).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val json = JsonParser.parseString(bodyStr).asJsonObject
                    json.get("id")?.asString?.replace("-", "") ?: ""
                } else ""
            }
        } catch (_: Exception) {
            ""
        }
    }
}
