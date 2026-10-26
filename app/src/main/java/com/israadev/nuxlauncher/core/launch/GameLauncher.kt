package com.israadev.nuxlauncher.core.launch

import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.israadev.nuxlauncher.GameActivity
import com.israadev.nuxlauncher.core.account.AccountManager
import com.israadev.nuxlauncher.core.account.microsoft.MicrosoftAuthService
import com.israadev.nuxlauncher.core.instance.InstanceManager
import com.israadev.nuxlauncher.core.models.Instance
import com.israadev.nuxlauncher.core.models.UserAccount
import com.israadev.nuxlauncher.core.models.VersionDetail
import com.israadev.nuxlauncher.ui.i18n.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

object GameLauncher {
    private val gson = Gson()

    // Batas menunggu pembaruan sesi Microsoft sebelum game tetap dijalankan.
    private const val SESSION_REFRESH_TIMEOUT_MS = 12_000L
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val refreshing = AtomicBoolean(false)

    /**
     * Menjalankan game. Untuk akun Microsoft yang token Minecraft-nya sudah lama, sesi diperbarui
     * dulu (token hanya berlaku sekitar 24 jam); tanpa ini server online menolak dengan "Invalid session".
     * Bila pembaruan gagal atau terlalu lama, game tetap dijalankan dengan token yang ada.
     */
    fun launch(context: Context, instance: Instance, account: UserAccount) {
        if (!MicrosoftAuthService.needsRefresh(account)) {
            launchNow(context, instance, account)
            return
        }
        // Ketukan ganda pada tombol main tidak boleh memulai dua pembaruan sekaligus.
        if (!refreshing.compareAndSet(false, true)) return

        val appContext = context.applicationContext
        Toast.makeText(appContext, "Memperbarui sesi Microsoft...", Toast.LENGTH_SHORT).show()
        refreshScope.launch {
            var launchAccount = account
            try {
                // Hasil disimpan di dalam tugas ini, jadi tetap tersimpan walau selesai setelah batas waktu.
                val job = async(Dispatchers.IO) {
                    MicrosoftAuthService.refreshMicrosoftAccount(account).also { result ->
                        result.getOrNull()?.let { fresh -> AccountManager.updateAccount(appContext, fresh) }
                    }
                }
                val result = withTimeoutOrNull(SESSION_REFRESH_TIMEOUT_MS) { job.await() }
                val fresh = result?.getOrNull()
                if (fresh != null) {
                    launchAccount = fresh
                } else {
                    android.util.Log.w("GameLauncher", "Sesi Microsoft tidak diperbarui: ${result?.exceptionOrNull()?.message ?: "melebihi batas waktu"}")
                    Toast.makeText(
                        appContext,
                        "Sesi Microsoft belum bisa diperbarui. Game tetap dijalankan, tetapi fitur online mungkin butuh login ulang.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                android.util.Log.w("GameLauncher", "Pembaruan sesi Microsoft gagal", e)
            } finally {
                refreshing.set(false)
            }
            launchNow(appContext, instance, launchAccount)
        }
    }

    private fun launchNow(context: Context, instance: Instance, account: UserAccount) {
        val gameDir = InstanceManager.getInstanceGameDir(context, instance)
        val versionDir = File(InstanceManager.getVersionsDir(context), instance.mcVersion)
        val versionJson = File(versionDir, "${instance.mcVersion}.json")

        var mainClass = "net.minecraft.client.main.Main"
        var assetIndexId = instance.mcVersion
        var mojangJavaMajor: Int? = null
        var versionDetail: VersionDetail? = null

        if (versionJson.exists()) {
            try {
                val detail = gson.fromJson(versionJson.readText(), VersionDetail::class.java)
                versionDetail = detail
                detail.mainClass?.let { mainClass = it }
                detail.assetIndex?.id?.let { assetIndexId = it }
                mojangJavaMajor = detail.javaVersion?.majorVersion
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val isFabric = instance.loader.equals("fabric", ignoreCase = true)
        var fabricProfile: com.israadev.nuxlauncher.core.models.FabricProfile? = null

        if (isFabric) {
            val fabricJsonFile = File(versionDir, "fabric-${instance.loaderVersion}.json")
            if (fabricJsonFile.exists()) {
                try {
                    fabricProfile = gson.fromJson(fabricJsonFile.readText(), com.israadev.nuxlauncher.core.models.FabricProfile::class.java)
                } catch (_: Exception) {}
            }
            if (fabricProfile == null) {
                val files = versionDir.listFiles { f -> f.name.startsWith("fabric-") && f.name.endsWith(".json") }
                if (!files.isNullOrEmpty()) {
                    try {
                        fabricProfile = gson.fromJson(files.first().readText(), com.israadev.nuxlauncher.core.models.FabricProfile::class.java)
                    } catch (_: Exception) {}
                }
            }

            if (fabricProfile == null) {
                com.israadev.nuxlauncher.ui.i18n.Toast.makeText(
                    context,
                    "Profil Fabric (${instance.loaderVersion}) belum terpasang. Silakan tekan tombol UNDUH pada instance!",
                    com.israadev.nuxlauncher.ui.i18n.Toast.LENGTH_LONG
                ).show()
                return
            }

            fabricProfile.mainClass?.let { mainClass = it } ?: run {
                mainClass = "net.fabricmc.loader.impl.launch.knot.KnotClient"
            }
        }

        // Forge/NeoForge: profil dibuat saat instance diunduh; peluncuran hanya membaca berkas kecil itu
        val forgeFlavor = com.israadev.nuxlauncher.core.forge.ForgeFlavor.of(instance.loader)
        var forgeLaunch: com.israadev.nuxlauncher.core.forge.ForgeLaunchInfo? = null
        if (forgeFlavor != null) {
            val forgeProfileFile = com.israadev.nuxlauncher.core.forge.ForgeInstaller.findProfileFile(
                context, instance.mcVersion, instance.loaderVersion, forgeFlavor
            )
            forgeLaunch = forgeProfileFile?.let {
                com.israadev.nuxlauncher.core.forge.ForgeLaunch.resolve(
                    it, InstanceManager.getLibrariesDir(context), instance.mcVersion
                )
            }
            if (forgeLaunch == null) {
                com.israadev.nuxlauncher.ui.i18n.Toast.makeText(
                    context,
                    "Profil ${forgeFlavor.displayName} (${instance.loaderVersion}) belum terpasang. Silakan tekan tombol UNDUH pada instance!",
                    com.israadev.nuxlauncher.ui.i18n.Toast.LENGTH_LONG
                ).show()
                return
            }
            mainClass = forgeLaunch.mainClass
        }

        val runtimeName = if (instance.javaRuntime != "auto") instance.javaRuntime else {
            com.israadev.nuxlauncher.core.runtime.JavaRuntimeManager.getRecommendedRuntime(instance.mcVersion, mojangJavaMajor)
        }
        val runtimeHome = com.israadev.nuxlauncher.core.runtime.JavaRuntimeManager.getRuntimeHome(context, runtimeName)

        // Prepare Android-native LWJGL components
        val versionJsonFile = File(versionDir, "${instance.mcVersion}.json")
        val lwjglVersion = LwjglManager.getLwjglVersion(instance.mcVersion, versionJsonFile)
        val lwjglNativesDir = LwjglManager.getNativesDir(context, lwjglVersion)
        val lwjglJars = LwjglManager.getLwjglJars(context, lwjglVersion)
        LwjglManager.prepareLauncherComponents(context)

        // Build classpath: Android LWJGL jars first, Fabric libraries, Mojang non-LWJGL libraries, then client.jar
        val libDir = InstanceManager.getLibrariesDir(context)
        val classpathEntries = mutableListOf<String>()

        // 1. Android LWJGL Jars
        lwjglJars.forEach { jar ->
            classpathEntries.add(jar.absolutePath)
        }

        // Add MioLaunchWrapper.jar if available
        val wrapperJar = LwjglManager.prepareLaunchWrapper(context)
        val useWrapper = wrapperJar != null && wrapperJar.exists()
        if (useWrapper) {
            classpathEntries.add(wrapperJar.absolutePath)
        }

        // 2. Fabric Libraries
        val loaderArtifactKeys = mutableSetOf<String>()
        if (isFabric && fabricProfile != null) {
            fabricProfile.libraries.forEach { lib ->
                if (lib.name.contains("org.lwjgl")) return@forEach
                val parts = lib.name.split(":")
                if (parts.size >= 2) {
                    loaderArtifactKeys.add("${parts[0]}:${parts[1]}")
                }
                val relPath = com.israadev.nuxlauncher.core.fabric.FabricService.artifactToPath(lib.name)
                if (relPath != null) {
                    val file = File(libDir, relPath)
                    if (file.exists()) {
                        classpathEntries.add(file.absolutePath)
                    }
                }
            }
        }

        // 2b. Forge libraries (library Mojang dengan group:artifact yang sama dilewati: versi Forge menang)
        if (forgeLaunch != null) {
            forgeLaunch.libraryFiles.forEach { classpathEntries.add(it.absolutePath) }
            loaderArtifactKeys.addAll(forgeLaunch.libraryKeys)
        }

        // 3. Mojang libraries (excluding desktop org.lwjgl and libraries already provided/overridden by mod loader)
        versionDetail?.libraries?.forEach { lib ->
            val name = lib.name ?: ""
            if (name.contains("org.lwjgl")) return@forEach

            val parts = name.split(":")
            if (parts.size >= 2) {
                val groupArtifact = "${parts[0]}:${parts[1]}"
                if (loaderArtifactKeys.contains(groupArtifact)) return@forEach
            }

            // Exclude vanilla ASM when running under Fabric (Fabric strictly provides & requires its own ASM)
            if (isFabric && (name.startsWith("org.ow2.asm:") || lib.downloads?.artifact?.path?.contains("org/ow2/asm") == true)) {
                return@forEach
            }

            val path = lib.downloads?.artifact?.path
            if (path != null) {
                val file = File(libDir, path)
                if (file.exists()) {
                    classpathEntries.add(file.absolutePath)
                }
            }
        }

        // 4. Minecraft Client JAR
        val clientJar = File(versionDir, "${instance.mcVersion}.jar")
        if (clientJar.exists()) {
            classpathEntries.add(clientJar.absolutePath)
        }

        val classpath = classpathEntries.distinct().joinToString(File.pathSeparator)
        val assetsDir = InstanceManager.getAssetsDir(context)

        val userType = if (account.safeAccountType == "microsoft") "msa" else "mojang"
        val accessToken = if (account.safeAccessToken.isNotBlank()) account.safeAccessToken else "0"

        val isElyBy = account.safeAccountType == "elyby"
        val isOffline = account.safeAccountType == "offline"

        var authlibJar: File? = null
        var authlibUrl: String? = null
        var playerUuid = account.uuid

        if (isElyBy) {
            authlibJar = LwjglManager.prepareAuthLibInjector(context)
            authlibUrl = account.authServerUrl ?: "https://authserver.ely.by/api/authlib-injector"

            // Pastikan UUID adalah 32-karakter valid yang dikenali server Ely.by
            if (playerUuid.length != 32 || playerUuid.toLongOrNull() != null) {
                val realUuid = com.israadev.nuxlauncher.core.account.elyby.ElyByAuthService.fetchRealPlayerUuid(account.username)
                if (realUuid.isNotBlank()) {
                    playerUuid = realUuid
                    val updated = account.copy(uuid = realUuid)
                    com.israadev.nuxlauncher.core.account.AccountManager.updateAccount(context, updated)
                }
            }
        } else if (isOffline) {
            val skinFile = account.getSkinFile()
            val capeFile = account.getCapeFile()
            val hasCustomSkinOrCape = (skinFile != null && skinFile.exists()) || (capeFile != null && capeFile.exists())

            if (hasCustomSkinOrCape) {
                // Hentikan server sebelumnya jika masih aktif
                com.israadev.nuxlauncher.core.account.offline.OfflineYggdrasilServer.activeInstance?.stop()

                val offlineServer = com.israadev.nuxlauncher.core.account.offline.OfflineYggdrasilServer(0)
                offlineServer.addCharacter(
                    username = account.username,
                    uuid = playerUuid,
                    skinFile = skinFile,
                    capeFile = capeFile,
                    isSlim = account.safeSkinModel == "slim"
                )
                val port = offlineServer.start()
                if (port > 0) {
                    authlibJar = LwjglManager.prepareAuthLibInjector(context)
                    authlibUrl = "http://127.0.0.1:$port"
                    android.util.Log.i("GameLauncher", "Mengaktifkan Offline Yggdrasil server pada port $port untuk ${account.username}")
                }
            }
        }

        val installedMods = com.israadev.nuxlauncher.core.crash.CrashManager.getInstalledMods(gameDir.absolutePath)

        val intent = Intent(context, GameActivity::class.java).apply {
            putExtra(GameActivity.EXTRA_INSTANCE_NAME, instance.name)
            putExtra(GameActivity.EXTRA_MC_VERSION, instance.mcVersion)
            putExtra(GameActivity.EXTRA_LOADER, instance.loader)
            putExtra(GameActivity.EXTRA_LOADER_VERSION, instance.loaderVersion)
            putStringArrayListExtra(GameActivity.EXTRA_INSTALLED_MODS, ArrayList(installedMods))
            putExtra(GameActivity.EXTRA_USERNAME, account.username)
            putExtra(GameActivity.EXTRA_UUID, playerUuid)
            putExtra(GameActivity.EXTRA_ACCESS_TOKEN, accessToken)
            putExtra(GameActivity.EXTRA_USER_TYPE, userType)
            if (authlibJar != null && authlibUrl != null) {
                putExtra(GameActivity.EXTRA_AUTHLIB_INJECTOR_PATH, authlibJar.absolutePath)
                putExtra(GameActivity.EXTRA_AUTHLIB_URL, authlibUrl)
            }
            putExtra(GameActivity.EXTRA_GAME_DIR, gameDir.absolutePath)
            putExtra(GameActivity.EXTRA_ASSETS_DIR, assetsDir.absolutePath)
            putExtra(GameActivity.EXTRA_ASSET_INDEX, assetIndexId)
            putExtra(GameActivity.EXTRA_MAIN_CLASS, mainClass)
            putExtra(GameActivity.EXTRA_CLASSPATH, classpath)
            putExtra(GameActivity.EXTRA_RUNTIME_NAME, runtimeName)
            putExtra(GameActivity.EXTRA_RUNTIME_HOME, runtimeHome.absolutePath)
            putExtra(GameActivity.EXTRA_LWJGL_NATIVES_DIR, lwjglNativesDir.absolutePath)
            putExtra(GameActivity.EXTRA_USE_WRAPPER, useWrapper)
            if (forgeLaunch != null) {
                putStringArrayListExtra(GameActivity.EXTRA_EXTRA_JVM_ARGS, ArrayList(forgeLaunch.jvmArgs))
                putStringArrayListExtra(GameActivity.EXTRA_EXTRA_GAME_ARGS, ArrayList(forgeLaunch.gameArgs))
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }
}
