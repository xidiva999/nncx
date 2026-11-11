package dev.nncx

import android.app.Application
import android.app.ActivityManager
import android.content.Context
import android.os.Process

class NncxApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // The fallback runs in its own process. If it installed the guard too,
        // a crash there would relaunch itself forever.
        if (!isFallbackProcess()) CrashGuard.install(this)
    }

    private fun isFallbackProcess(): Boolean {
        val mine = Process.myPid()
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val name = am.runningAppProcesses?.firstOrNull { it.pid == mine }?.processName
        return name?.endsWith(":fallback") == true
    }
}
