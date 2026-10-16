package com.riskauditor.app.scan

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import android.provider.Settings
import com.riskauditor.app.model.*

class AppScanner(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val selfPackage: String = context.packageName

    fun scanAll(includeSystemApps: Boolean): List<AppInfo> {
        val flags = PackageManager.GET_PERMISSIONS

        val packages = pm.getInstalledPackages(flags).filter { pkg ->
            if (pkg.packageName == selfPackage) return@filter false
            val ai = pkg.applicationInfo ?: return@filter false
            includeSystemApps || (ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0
        }

        return packages.mapNotNull { pkg ->
            try {
                buildAppInfo(pkg)
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.label.lowercase() }
    }

    private fun buildAppInfo(pkg: PackageInfo): AppInfo {
        val ai = pkg.applicationInfo ?: throw IllegalStateException("No app info")
        val label = pm.getApplicationLabel(ai).toString()
        val icon = try { pm.getApplicationIcon(ai) } catch (e: Exception) { null }

        val declared = extractDeclaredPermissions(pkg)
        val granted = declared.filter { it.granted }
        val special = detectSpecialAccess(pkg.packageName)
        val categorized = categorizePermissions(declared)

        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(pkg.packageName).installingPackageName ?: "Sideloaded/Unknown"
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(pkg.packageName) ?: "Sideloaded/Unknown"
        }

        return AppInfo(
            packageName = pkg.packageName,
            label = label,
            icon = icon,
            versionName = pkg.versionName ?: "?",
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                pkg.longVersionCode else @Suppress("DEPRECATION") pkg.versionCode.toLong(),
            installTime = pkg.firstInstallTime,
            updateTime = pkg.lastUpdateTime,
            sourceDir = ai.sourceDir ?: "",
            installerSource = installer,
            targetSdk = ai.targetSdkVersion,
            isSystemApp = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            declaredPermissions = declared,
            grantedPermissions = granted,
            specialAccess = special,
            categoryPermissions = categorized
        )
    }

    private fun extractDeclaredPermissions(pkg: PackageInfo): List<PermissionEntry> {
        val requested = pkg.requestedPermissions ?: return emptyList()
        val ownUid = android.os.Process.myUid()

        return requested.map { permName ->
            val protection = getProtectionLevel(permName)
            val isGranted = try {
                pm.checkPermission(permName, pkg.packageName) == PackageManager.PERMISSION_GRANTED
            } catch (e: Exception) {
                false
            }
            PermissionEntry(
                name = permName,
                granted = isGranted,
                protectionLevel = protection
            )
        }
    }

    private fun getProtectionLevel(permissionName: String): String {
        return try {
            @Suppress("DEPRECATION")
            val pi = pm.getPermissionInfo(permissionName, 0)
            val level = pi.protection
            when {
                level and PermissionInfo.PROTECTION_FLAG_PRIVILEGED != 0 -> "special"
                level and PermissionInfo.PROTECTION_FLAG_DEVELOPMENT != 0 -> "development"
                level == PermissionInfo.PROTECTION_DANGEROUS -> "dangerous"
                level == PermissionInfo.PROTECTION_SIGNATURE -> "signature"
                level == PermissionInfo.PROTECTION_NORMAL -> "normal"
                else -> "unknown"
            }
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun detectSpecialAccess(packageName: String): SpecialAccessInfo {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

        val accessibility = isAccessibilityServiceEnabled(packageName)
        val deviceAdmin = isDeviceAdminActive(packageName, dpm)
        val notifListener = isNotificationListenerEnabled(packageName)
        val overlay = checkOverlayPermission(packageName, appOps)
        val installPkgs = checkAppOp(packageName, "REQUEST_INSTALL_PACKAGES", appOps)
        val usageStats = checkAppOp(packageName, "GET_USAGE_STATS", appOps)
        val vpn = isVpnService(packageName)
        val notifPolicy = isNotificationPolicyAccess(packageName)

        return SpecialAccessInfo(
            accessibilityServiceEnabled = accessibility.isNotEmpty(),
            accessibilityServiceNames = accessibility,
            deviceAdminActive = deviceAdmin,
            deviceAdminNames = if (deviceAdmin) listOf(packageName) else emptyList(),
            notificationListenerEnabled = notifListener.isNotEmpty(),
            notificationListenerNames = notifListener,
            canDrawOverlays = overlay && hasOverlayDeclared(packageName),
            requestInstallPackages = installPkgs,
            packageUsageStats = usageStats,
            vpnService = vpn,
            notificationPolicyAccess = notifPolicy
        )
    }

    private fun isAccessibilityServiceEnabled(packageName: String): List<String> {
        return try {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return emptyList()

            enabled.split(":").filter { it.startsWith(packageName) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun isDeviceAdminActive(packageName: String, dpm: DevicePolicyManager): Boolean {
        return try {
            val admins = dpm.getActiveAdmins() ?: return false
            admins.any { it.packageName == packageName }
        } catch (e: Exception) {
            false
        }
    }

    private fun isNotificationListenerEnabled(packageName: String): List<String> {
        return try {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return emptyList()

            enabled.split(":").filter { it.startsWith(packageName) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun hasOverlayDeclared(packageName: String): Boolean {
        return try {
            val pi = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            pi.requestedPermissions?.contains(Manifest.permission.SYSTEM_ALERT_WINDOW) == true
        } catch (e: Exception) {
            false
        }
    }

    private fun checkAppOp(packageName: String, op: String, appOps: AppOpsManager): Boolean {
        return try {
            val uid = pm.getApplicationInfo(packageName, 0).uid
            val mode = appOps.checkOpNoThrow(op, uid, packageName)
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    private fun checkOverlayPermission(packageName: String, appOps: AppOpsManager): Boolean {
        return try {
            val uid = pm.getApplicationInfo(packageName, 0).uid
            val mode = appOps.checkOpNoThrow("SYSTEM_ALERT_WINDOW", uid, packageName)
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    private fun isVpnService(packageName: String): Boolean {
        return try {
            val intent = Intent("android.net.VpnService")
            intent.setPackage(packageName)
            val services = pm.queryIntentServices(intent, 0)
            services.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    private fun isNotificationPolicyAccess(packageName: String): Boolean {
        return try {
            checkAppOp(packageName, "ACCESS_NOTIFICATION_POLICY",
                context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager)
        } catch (e: Exception) {
            false
        }
    }

    private fun categorizePermissions(permissions: List<PermissionEntry>): Map<PermissionCategory, List<PermissionEntry>> {
        val result = mutableMapOf<PermissionCategory, MutableList<PermissionEntry>>()
        PermissionCategory.entries.forEach { result[it] = mutableListOf() }

        permissions.forEach { perm ->
            val cat = categorizeSingle(perm.name)
            result[cat]?.add(perm)
        }

        return result.filterValues { it.isNotEmpty() }
    }

    private fun categorizeSingle(permission: String): PermissionCategory {
        return when {
            permission.contains("SYSTEM_ALERT_WINDOW") -> PermissionCategory.SPECIAL_ACCESS
            permission.contains("BIND_ACCESSIBILITY_SERVICE") -> PermissionCategory.SPECIAL_ACCESS
            permission.contains("BIND_DEVICE_ADMIN") -> PermissionCategory.SPECIAL_ACCESS
            permission.contains("BIND_NOTIFICATION_LISTENER_SERVICE") -> PermissionCategory.SPECIAL_ACCESS
            permission.contains("REQUEST_INSTALL_PACKAGES") -> PermissionCategory.SPECIAL_ACCESS
            permission.contains("PACKAGE_USAGE_STATS") -> PermissionCategory.SPECIAL_ACCESS
            permission.contains("MANAGE_EXTERNAL_STORAGE") -> PermissionCategory.SPECIAL_ACCESS
            permission.contains("REQUEST_DELETE_PACKAGES") -> PermissionCategory.SPECIAL_ACCESS
            permission.contains("WRITE_SECURE_SETTINGS") -> PermissionCategory.SPECIAL_ACCESS
            permission.contains("BIND_VPN_SERVICE") -> PermissionCategory.SPECIAL_ACCESS
            permission.contains("ACCESS_NOTIFICATION_POLICY") -> PermissionCategory.SPECIAL_ACCESS

            permission.contains("SMS") || permission.contains("MMS") -> PermissionCategory.SMS

            permission.contains("CONTACTS") -> PermissionCategory.CONTACTS_CALL_LOGS
            permission.contains("CALL_LOG") -> PermissionCategory.CONTACTS_CALL_LOGS

            permission.contains("CAMERA") -> PermissionCategory.CAMERA_MIC
            permission.contains("RECORD_AUDIO") -> PermissionCategory.CAMERA_MIC

            permission.contains("LOCATION") -> PermissionCategory.LOCATION

            permission.contains("PHONE_STATE") -> PermissionCategory.PHONE_IDENTITY
            permission.contains("PHONE_NUMBERS") -> PermissionCategory.PHONE_IDENTITY
            permission.contains("CALL_PHONE") -> PermissionCategory.PHONE_IDENTITY
            permission.contains("ANSWER_PHONE_CALLS") -> PermissionCategory.PHONE_IDENTITY

            permission.contains("EXTERNAL_STORAGE") -> PermissionCategory.STORAGE_MEDIA
            permission.contains("READ_MEDIA") -> PermissionCategory.STORAGE_MEDIA
            permission.contains("WRITE_EXTERNAL") -> PermissionCategory.STORAGE_MEDIA
            permission.contains("READ_EXTERNAL") -> PermissionCategory.STORAGE_MEDIA

            permission.contains("BOOT_COMPLETED") -> PermissionCategory.BACKGROUND_PERSISTENCE
            permission.contains("FOREGROUND_SERVICE") -> PermissionCategory.BACKGROUND_PERSISTENCE
            permission.contains("IGNORE_BATTERY") -> PermissionCategory.BACKGROUND_PERSISTENCE
            permission.contains("WAKE_LOCK") -> PermissionCategory.BACKGROUND_PERSISTENCE

            permission.contains("INTERNET") -> PermissionCategory.NETWORK
            permission.contains("ACCESS_NETWORK") -> PermissionCategory.NETWORK
            permission.contains("CHANGE_NETWORK") -> PermissionCategory.NETWORK
            permission.contains("ACCESS_WIFI") -> PermissionCategory.NETWORK

            else -> PermissionCategory.NETWORK
        }
    }
}
