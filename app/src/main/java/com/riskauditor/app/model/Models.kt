package com.riskauditor.app.model

import android.graphics.drawable.Drawable

enum class RiskBand(val label: String, val colorRes: Int) {
    LOW("Low", android.R.color.holo_green_dark),
    MEDIUM("Medium", android.R.color.holo_orange_dark),
    HIGH("High", android.R.color.holo_red_dark),
    CRITICAL("Critical", android.R.color.holo_red_dark);

    companion object {
        fun fromScore(score: Int): RiskBand = when {
            score >= 75 -> CRITICAL
            score >= 50 -> HIGH
            score >= 25 -> MEDIUM
            else -> LOW
        }
    }
}

enum class PermissionCategory(val displayName: String, val weight: Int) {
    SPECIAL_ACCESS("Special Access", 30),
    SMS("SMS", 15),
    CONTACTS_CALL_LOGS("Contacts & Call Logs", 12),
    CAMERA_MIC("Camera & Microphone", 10),
    LOCATION("Location", 12),
    PHONE_IDENTITY("Phone & Identity", 8),
    STORAGE_MEDIA("Storage & Media", 8),
    BACKGROUND_PERSISTENCE("Background & Persistence", 10),
    NETWORK("Network", 5)
}

data class PermissionEntry(
    val name: String,
    val granted: Boolean,
    val protectionLevel: String
)

data class SpecialAccessInfo(
    val accessibilityServiceEnabled: Boolean = false,
    val accessibilityServiceNames: List<String> = emptyList(),
    val deviceAdminActive: Boolean = false,
    val deviceAdminNames: List<String> = emptyList(),
    val notificationListenerEnabled: Boolean = false,
    val notificationListenerNames: List<String> = emptyList(),
    val canDrawOverlays: Boolean = false,
    val requestInstallPackages: Boolean = false,
    val packageUsageStats: Boolean = false,
    val vpnService: Boolean = false,
    val notificationPolicyAccess: Boolean = false
)

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val versionName: String,
    val versionCode: Long,
    val installTime: Long,
    val updateTime: Long,
    val sourceDir: String,
    val installerSource: String,
    val targetSdk: Int,
    val isSystemApp: Boolean,
    val declaredPermissions: List<PermissionEntry>,
    val grantedPermissions: List<PermissionEntry>,
    val specialAccess: SpecialAccessInfo,
    val categoryPermissions: Map<PermissionCategory, List<PermissionEntry>>
)

data class RiskReason(
    val text: String,
    val severity: String
)

data class RiskResult(
    val score: Int,
    val band: RiskBand,
    val reasons: List<RiskReason>,
    val categoryScores: Map<PermissionCategory, Int>,
    val combinationRules: List<String>,
    val contextMismatches: List<String>
)

data class DeviceScanResult(
    val apps: List<Pair<AppInfo, RiskResult>>,
    val totalUserApps: Int,
    val totalSystemApps: Int,
    val criticalCount: Int,
    val highCount: Int,
    val mediumCount: Int,
    val lowCount: Int,
    val scanTimeMs: Long
)
