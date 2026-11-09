package com.israadev.nuxlauncher.core.account

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.israadev.nuxlauncher.core.models.UserAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

object AccountManager {
    private val gson = Gson()

    // Akun Minecraft (profil pemain di halaman Akun)
    private val _accounts = MutableStateFlow<List<UserAccount>>(emptyList())
    val accounts: StateFlow<List<UserAccount>> = _accounts.asStateFlow()

    private val _currentAccount = MutableStateFlow<UserAccount?>(null)
    val currentAccount: StateFlow<UserAccount?> = _currentAccount.asStateFlow()

    private fun getAccountsFile(context: Context): File {
        return File(context.filesDir, "accounts.json")
    }

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        val accountsFile = getAccountsFile(context)
        if (accountsFile.exists()) {
            try {
                val json = accountsFile.readText()
                val type = object : TypeToken<List<UserAccount>>() {}.type
                val rawList: List<UserAccount> = gson.fromJson(json, type) ?: emptyList()
                val list: List<UserAccount> = rawList.map { acc ->
                    acc.copy(
                        id = if (acc.id.isNullOrBlank()) java.util.UUID.randomUUID().toString() else acc.id,
                        username = if (acc.username.isNullOrBlank()) "Player" else acc.username,
                        uuid = if (acc.uuid.isNullOrBlank()) java.util.UUID.randomUUID().toString() else acc.uuid,
                        accessToken = acc.safeAccessToken,
                        refreshToken = acc.safeRefreshToken,
                        email = acc.safeEmail,
                        tier = acc.safeTier,
                        accountType = acc.safeAccountType,
                        skinModel = acc.safeSkinModel
                    )
                }

                // Buang sisa akun bawaan lama "NuxPlayer" dari daftar profil game
                val cleanGameList = list.filterNot {
                    it.username.equals("NuxPlayer", ignoreCase = true)
                }

                _accounts.value = cleanGameList
                _currentAccount.value = cleanGameList.firstOrNull()
                saveGameAccounts(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ==========================================
    // MINECRAFT GAME ACCOUNTS OPERATIONS
    // ==========================================

    fun createGuestAccount(username: String): UserAccount {
        val clean = username.trim().ifBlank { "Player" }
        return UserAccount(
            id = UUID.randomUUID().toString(),
            username = clean,
            uuid = UUID.nameUUIDFromBytes("OfflinePlayer:$clean".toByteArray()).toString(),
            isOffline = true,
            accountType = "offline"
        )
    }

    fun addAccount(context: Context, account: UserAccount) {
        val list = _accounts.value.toMutableList()
        list.removeAll { it.id == account.id || (it.username.equals(account.username, ignoreCase = true) && it.safeAccountType == account.safeAccountType) }
        list.add(0, account)
        _accounts.value = list
        _currentAccount.value = account
        saveGameAccounts(context)
    }

    fun updateAccount(context: Context, account: UserAccount) {
        val list = _accounts.value.toMutableList()
        val index = list.indexOfFirst {
            it.id == account.id || (it.username.equals(account.username, ignoreCase = true) && it.safeAccountType == account.safeAccountType)
        }
        if (index != -1) {
            list[index] = account
        } else {
            list.add(0, account)
        }
        _accounts.value = list
        val isCurrentMatch = _currentAccount.value?.id == account.id ||
            (_currentAccount.value?.username?.equals(account.username, ignoreCase = true) == true &&
             _currentAccount.value?.safeAccountType == account.safeAccountType)
        if (isCurrentMatch || _currentAccount.value == null) {
            _currentAccount.value = account
        }
        saveGameAccounts(context)
    }

    fun selectAccount(account: UserAccount) {
        _currentAccount.value = account
    }

    fun deleteAccount(context: Context, account: UserAccount) {
        val list = _accounts.value.toMutableList()
        list.removeAll { it.id == account.id || it.username.equals(account.username, ignoreCase = true) }
        _accounts.value = list
        if (_currentAccount.value?.id == account.id || _currentAccount.value?.username.equals(account.username, ignoreCase = true)) {
            _currentAccount.value = list.firstOrNull()
        }
        saveGameAccounts(context)
    }

    fun removeAccount(context: Context, account: UserAccount) {
        deleteAccount(context, account)
    }

    private fun saveGameAccounts(context: Context) {
        try {
            val file = getAccountsFile(context)
            com.israadev.nuxlauncher.core.utils.AtomicFiles.writeText(file, gson.toJson(_accounts.value))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
