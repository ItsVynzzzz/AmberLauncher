package com.israadev.nuxlauncher

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.israadev.nuxlauncher.core.crash.CrashManager
import com.israadev.nuxlauncher.core.crash.LauncherLog
import com.israadev.nuxlauncher.ui.activities.ErrorActivity
import com.israadev.nuxlauncher.ui.i18n.Tr

class NuxApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Buku harian kejadian launcher (tiap proses): masalah di luar game tercatat di sini.
        try {
            LauncherLog.init(this)
            registerLifecycleLogging()
        } catch (_: Throwable) {
        }

        // Bahasa tampilan (Indonesia / English). Dipanggil di tiap proses, termasuk proses game.
        try {
            Tr.init(this)
        } catch (_: Throwable) {
        }

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                LauncherLog.crash(thread, throwable)
            } catch (_: Throwable) {
            }
            try {
                Log.e("NuxApplication", "Uncaught exception on thread ${thread.name}", throwable)
                // Catat ke sistem crash manager
                CrashManager.recordLauncherCrash(this, throwable)
                // Tampilkan ErrorActivity mandiri
                ErrorActivity.showLauncherCrash(this, throwable)
            } catch (e: Exception) {
                Log.e("NuxApplication", "Failed to dispatch crash activity", e)
                defaultHandler?.uncaughtException(thread, throwable)
            }

            // Hentikan proses yang mengalami crash agar tidak memicu ANR / loop
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(10)
        }
    }

    /** Mencatat layar yang dibuka dan ditutup (tanpa logcat; hanya ke buku harian launcher). */
    private fun registerLifecycleLogging() {
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                LauncherLog.event(
                    "Layar",
                    activity.javaClass.simpleName + " dibuka" + (if (savedInstanceState != null) " (dipulihkan)" else "")
                )
            }

            override fun onActivityDestroyed(activity: Activity) {
                LauncherLog.event(
                    "Layar",
                    activity.javaClass.simpleName + " ditutup" + (if (activity.isFinishing) "" else " (dibuat ulang oleh sistem)")
                )
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        })
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // 10 = RAM mulai sempit, 15 = kritis, 40 ke atas = proses ini berada di latar dan bisa dimatikan Android.
        if (level == 10 || level == 15 || level >= 40) {
            LauncherLog.event("Memori", "Android meminta aplikasi melepas memori (tingkat $level)")
        }
    }
}
