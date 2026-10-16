package com.riskauditor.app.util

import android.content.Context
import android.content.SharedPreferences

object SafeAppsManager {
    private const val PREFS_NAME = "safe_apps"
    private const val KEY_SAFE_APPS = "safe_packages"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSafeApps(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_SAFE_APPS, emptySet()) ?: emptySet()
    }

    fun isSafe(context: Context, packageName: String): Boolean {
        return getSafeApps(context).contains(packageName)
    }

    fun markSafe(context: Context, packageName: String) {
        val current = getSafeApps(context).toMutableSet()
        current.add(packageName)
        getPrefs(context).edit().putStringSet(KEY_SAFE_APPS, current).apply()
    }

    fun unmarkSafe(context: Context, packageName: String) {
        val current = getSafeApps(context).toMutableSet()
        current.remove(packageName)
        getPrefs(context).edit().putStringSet(KEY_SAFE_APPS, current).apply()
    }

    fun toggleSafe(context: Context, packageName: String): Boolean {
        return if (isSafe(context, packageName)) {
            unmarkSafe(context, packageName)
            false
        } else {
            markSafe(context, packageName)
            true
        }
    }
}
