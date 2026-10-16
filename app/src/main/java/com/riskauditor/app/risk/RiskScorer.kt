package com.riskauditor.app.risk

import com.riskauditor.app.model.*

class RiskScorer {

    companion object {
        // Trusted package prefixes — Google, AOSP, OEM essentials
        private val TRUSTED_PREFIXES = listOf(
            "com.google.android.",
            "com.android.",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.android.chrome",
            "com.google.android.music",
            "com.google.android.videos",
            "com.google.android.apps.maps",
            "com.google.android.youtube",
            "com.google.android.keep",
            "com.google.android.contacts",
            "com.google.android.dialer",
            "com.google.android.calendar",
            "com.google.android.calendar",
            "com.google.android.email",
            "com.google.android.gm",
            "com.google.android.apps.photos",
            "com.google.android.apps.docs",
            "com.google.android.apps.messaging",
            "com.google.android.deskclock",
            "com.google.android.calculator",
            "com.google.android.calendar",
            "com.google.android.soundrecorder",
            "com.google.android.gallery3d",
            "com.google.android.printspooler",
            "com.google.android.webview",
            "com.google.android.partnersetup",
            "com.google.android.setupwizard",
            "com.google.android.backup",
            "com.google.android.configupdater",
            "com.google.android.feedback",
            "com.google.android.googlequicksearchbox",
            "com.google.android.marvin.talkback",
            "com.google.android.onetimeinitializer",
            "com.google.android.partnersetup",
            "com.google.android.projection.gearhead",
            "com.google.android.safetyhub",
            "com.google.android.soundpicker",
            "com.google.android.syncadapters.contacts",
            "com.google.android.syncadapters.calendar",
            "com.google.android.tag",
            "com.google.android.talk",
            "com.google.android.tts",
            "com.google.android.inputmethod.latin",
            "com.google.android.music",
            "com.google.android.vending",
            "com.google.android.play.games",
            "com.google.android.apps.walletnfcrel",
            "com.google.android.gms.policy_sidecar_aps",
            "com.android.chrome",
            "com.android.vending",
            "com.android.contacts",
            "com.android.dialer",
            "com.android.mms",
            "com.android.calendar",
            "com.android.calculator2",
            "com.android.deskclock",
            "com.android.camera",
            "com.android.gallery3d",
            "com.android.email",
            "com.android.music",
            "com.android.settings",
            "com.android.systemui",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.bluetooth",
            "com.android.nfc",
            "com.android.stk",
            "com.android.certinstaller",
            "com.android.keychain",
            "com.android.location.fused",
            "com.android.managedprovisioning",
            "com.android.printspooler",
            "com.android.printservice.recommendation",
            "com.android.traceur",
            "com.android.intentresolver",
            "com.android.carrierconfig",
            "com.android.emulator.multidisplay",
            "com.android.healthconnect.controller",
            "com.google.android.permissioncontroller",
            "com.google.android.modulemetadata",
            "com.google.android.ext.services",
            "com.google.android.ext.shared",
            "com.google.android.networkstack",
            "com.google.android.networkstack.tethering",
            "com.google.android.reservoir",
            "com.google.mainline.telemetry",
            // Common OEM/manufacturer trusted
            "com.samsung.android.",
            "com.sec.android.",
            "com.miui.",
            "com.mi.",
            "com.huawei.",
            "com.oneplus.",
            "com.oppo.",
            "com.vivo.",
            "com.realme.",
            "com.asus.",
            "com.lenovo.",
            "com.motorola.",
            "com.htc.",
            "com.lge.",
            "com.sonymobile.",
            "com.blackberry.",
            "com.nothing.",
            "com.noosoda.",
            "com.nothing.edts"
        )

        // Known security/MDM/privacy tools that legitimately use special access
        private val SECURITY_APPS = listOf(
            "bitwarden", "lastpass", "1password", "dashlane", "keeper",
            "norton", "mcafee", "avast", "avg", "kaspersky", "bitdefender",
            "malwarebytes", "lookout", "sophos", "eset", "f-secure",
            "intune", "workspace", "knox", "mdm", "endpoint",
            "prey", "cerberus", "find my", "device manager",
            "find my device", "google find my device",
            "proton", "signal", "threema", "wickr",
            "duckduckgo", "brave", "firefox", "tor"
        )

        // Accessibility tools that legitimately use accessibility services
        private val ACCESSIBILITY_TOOLS = listOf(
            "accessibility", "talkback", "switch access", "select to speak",
            "tasker", "autoinput", "autoapps", "macro",
            "button mapper", "volume", "navigation",
            "bitwarden", "lastpass", "1password", "dashlane", "keeper",
            "blind", "visually", "screen reader", "braille"
        )
    }

    fun score(app: AppInfo): RiskResult {
        val categoryScores = mutableMapOf<PermissionCategory, Int>()
        val reasons = mutableListOf<RiskReason>()
        var totalScore = 0

        val isTrusted = isTrustedApp(app)
        val isSecurityApp = isSecurityApp(app)
        val isAccessibilityTool = isAccessibilityTool(app)

        // --- Score each category ---
        // SPECIAL ACCESS
        val specialScore = scoreSpecialAccess(app.specialAccess, reasons, isSecurityApp, isAccessibilityTool)
        categoryScores[PermissionCategory.SPECIAL_ACCESS] = specialScore
        totalScore += specialScore

        // SMS
        val smsGranted = app.grantedPermissions.filter { it.name.contains("SMS") && it.granted }
        if (smsGranted.isNotEmpty()) {
            val s = if (isTrusted) 3 else PermissionCategory.SMS.weight
            categoryScores[PermissionCategory.SMS] = s
            totalScore += s
            if (!isTrusted) {
                reasons.add(RiskReason("Can read/send SMS — OTP theft and premium SMS fraud risk", "HIGH"))
            }
        }

        // Contacts & Call Logs
        val contactsGranted = app.grantedPermissions.filter {
            (it.name.contains("CONTACTS") || it.name.contains("CALL_LOG")) && it.granted
        }
        if (contactsGranted.isNotEmpty()) {
            val s = if (isTrusted) 2 else PermissionCategory.CONTACTS_CALL_LOGS.weight
            categoryScores[PermissionCategory.CONTACTS_CALL_LOGS] = s
            totalScore += s
            if (!isTrusted) {
                reasons.add(RiskReason("Can access contacts and call logs — social graph harvesting risk", "MEDIUM"))
            }
        }

        // Camera & Mic
        val camMicGranted = app.grantedPermissions.filter {
            (it.name.contains("CAMERA") || it.name.contains("RECORD_AUDIO")) && it.granted
        }
        if (camMicGranted.isNotEmpty()) {
            val s = if (isTrusted) 2 else PermissionCategory.CAMERA_MIC.weight
            categoryScores[PermissionCategory.CAMERA_MIC] = s
            totalScore += s
            if (!isTrusted) {
                reasons.add(RiskReason("Can use camera and microphone — covert recording risk", "MEDIUM"))
            }
        }

        // Location
        val locGranted = app.grantedPermissions.filter { it.name.contains("LOCATION") && it.granted }
        if (locGranted.isNotEmpty()) {
            val s = if (isTrusted) 2 else PermissionCategory.LOCATION.weight
            categoryScores[PermissionCategory.LOCATION] = s
            totalScore += s
            if (!isTrusted) {
                val hasBackground = locGranted.any { it.name.contains("BACKGROUND") }
                reasons.add(RiskReason(
                    if (hasBackground) "Can track your location in background — stalking/tracking risk"
                    else "Can access your location",
                    if (hasBackground) "HIGH" else "LOW"
                ))
            }
        }

        // Phone & Identity
        val phoneGranted = app.grantedPermissions.filter {
            (it.name.contains("PHONE_STATE") || it.name.contains("PHONE_NUMBERS") ||
                    it.name.contains("CALL_PHONE")) && it.granted
        }
        if (phoneGranted.isNotEmpty()) {
            val s = if (isTrusted) 1 else PermissionCategory.PHONE_IDENTITY.weight
            categoryScores[PermissionCategory.PHONE_IDENTITY] = s
            totalScore += s
        }

        // Storage & Media
        val storageGranted = app.grantedPermissions.filter {
            (it.name.contains("EXTERNAL_STORAGE") || it.name.contains("READ_MEDIA") ||
                    it.name.contains("MANAGE_EXTERNAL")) && it.granted
        }
        if (storageGranted.isNotEmpty()) {
            val s = if (isTrusted) 1 else PermissionCategory.STORAGE_MEDIA.weight
            categoryScores[PermissionCategory.STORAGE_MEDIA] = s
            totalScore += s
            if (!isTrusted && storageGranted.any { it.name.contains("MANAGE_EXTERNAL") }) {
                reasons.add(RiskReason("Has full file access — can read all files on device", "HIGH"))
            }
        }

        // Background & Persistence
        val bgGranted = app.grantedPermissions.filter {
            (it.name.contains("BOOT_COMPLETED") || it.name.contains("FOREGROUND_SERVICE") ||
                    it.name.contains("IGNORE_BATTERY")) && it.granted
        }
        if (bgGranted.isNotEmpty()) {
            val s = if (isTrusted) 0 else PermissionCategory.BACKGROUND_PERSISTENCE.weight
            categoryScores[PermissionCategory.BACKGROUND_PERSISTENCE] = s
            totalScore += s
            if (!isTrusted && bgGranted.isNotEmpty()) {
                reasons.add(RiskReason("Runs in background and survives reboots — persistent access", "MEDIUM"))
            }
        }

        // Network
        val netGranted = app.grantedPermissions.filter {
            (it.name.contains("INTERNET") || it.name.contains("NETWORK")) && it.granted
        }
        if (netGranted.isNotEmpty()) {
            val s = if (isTrusted) 0 else PermissionCategory.NETWORK.weight
            categoryScores[PermissionCategory.NETWORK] = s
            totalScore += s
        }

        // --- Combination rules (skip for trusted apps) ---
        var combinationRuleNames = emptyList<String>()
        if (!isTrusted) {
            val (comboBonus, comboNames) = evaluateCombinationRules(app, reasons, isSecurityApp, isAccessibilityTool)
            totalScore += comboBonus
            combinationRuleNames = comboNames
        }

        // --- Context mismatch (skip for trusted apps) ---
        var contextMismatches = emptyList<String>()
        if (!isTrusted) {
            contextMismatches = detectContextMismatches(app, reasons)
            totalScore += contextMismatches.size * 8
        }

        // --- Sideload penalty (never for trusted system apps) ---
        if (!isTrusted && !app.isSystemApp) {
            val isSideloaded = app.installerSource == "Sideloaded/Unknown" ||
                    app.installerSource == "null" || app.installerSource.isEmpty()
            if (isSideloaded && totalScore > 30) {
                reasons.add(RiskReason("Sideloaded (not from Play Store) with elevated permissions — high trust risk", "HIGH"))
                totalScore += 15
            }
        }

        // --- Trusted system app floor reduction ---
        if (isTrusted && app.isSystemApp) {
            totalScore = (totalScore * 0.3).toInt()
            if (reasons.isEmpty() && totalScore > 10) {
                reasons.add(RiskReason("System app with expected permissions — low actual risk", "LOW"))
            }
        } else if (isTrusted) {
            totalScore = (totalScore * 0.5).toInt()
        }

        // Cap score
        totalScore = totalScore.coerceIn(0, 100)
        val band = RiskBand.fromScore(totalScore)

        // Ensure at least 3 reasons for High/Critical (non-trusted only)
        if ((band == RiskBand.HIGH || band == RiskBand.CRITICAL) && !isTrusted) {
            while (reasons.size < 3) {
                reasons.add(RiskReason("Combination of sensitive permissions exceeds safe threshold", "HIGH"))
            }
        }

        // For trusted apps that somehow still score high, explain why
        if (isTrusted && (band == RiskBand.HIGH || band == RiskBand.CRITICAL)) {
            reasons.add(0, RiskReason(
                "This is a trusted system/Google app — permissions are expected for its function. Score may be inflated by permission count.",
                "LOW"
            ))
        }

        return RiskResult(
            score = totalScore,
            band = band,
            reasons = reasons,
            categoryScores = categoryScores,
            combinationRules = combinationRuleNames,
            contextMismatches = contextMismatches
        )
    }

    private fun scoreSpecialAccess(
        info: SpecialAccessInfo,
        reasons: MutableList<RiskReason>,
        isSecurityApp: Boolean,
        isAccessibilityTool: Boolean
    ): Int {
        var score = 0

        if (info.accessibilityServiceEnabled) {
            val pts = if (isAccessibilityTool) 5 else 25
            score += pts
            if (!isAccessibilityTool) {
                reasons.add(RiskReason(
                    "Accessibility service ACTIVE — can read all screen content, intercept taps, capture text from banking/OTP screens",
                    "CRITICAL"
                ))
            }
        }

        if (info.deviceAdminActive) {
            val pts = if (isSecurityApp) 5 else 20
            score += pts
            if (!isSecurityApp) {
                reasons.add(RiskReason(
                    "Device Administrator ACTIVE — can lock screen, wipe data, prevent uninstall",
                    "CRITICAL"
                ))
            }
        }

        if (info.notificationListenerEnabled) {
            score += 15
            reasons.add(RiskReason(
                "Notification Listener ACTIVE — can read all notifications including OTPs and banking alerts",
                "CRITICAL"
            ))
        }

        if (info.canDrawOverlays) {
            score += 15
            reasons.add(RiskReason(
                "Screen overlay permission — can draw fake login screens over other apps",
                "HIGH"
            ))
        }

        if (info.requestInstallPackages) {
            score += 10
            reasons.add(RiskReason("Can install other APKs — may install additional malware", "HIGH"))
        }

        if (info.packageUsageStats) {
            score += 5
            reasons.add(RiskReason("Can see which apps you use and when", "MEDIUM"))
        }

        if (info.vpnService) {
            score += 8
            reasons.add(RiskReason("VPN service declared — can intercept all network traffic", "HIGH"))
        }

        if (info.notificationPolicyAccess) {
            score += 5
            reasons.add(RiskReason("Can modify notification settings — may suppress security alerts", "MEDIUM"))
        }

        return score.coerceIn(0, 30)
    }

    private fun evaluateCombinationRules(
        app: AppInfo,
        reasons: MutableList<RiskReason>,
        isSecurityApp: Boolean,
        isAccessibilityTool: Boolean
    ): Pair<Int, List<String>> {
        var bonus = 0
        val ruleNames = mutableListOf<String>()
        val granted = app.grantedPermissions.filter { it.granted }.map { it.name }

        val hasSms = granted.any { it.contains("SMS") }
        val hasInternet = granted.any { it.contains("INTERNET") }
        val hasBoot = granted.any { it.contains("BOOT_COMPLETED") }
        val hasAccessibility = app.specialAccess.accessibilityServiceEnabled
        val hasOverlay = app.specialAccess.canDrawOverlays
        val hasDeviceAdmin = app.specialAccess.deviceAdminActive
        val hasNotifListener = app.specialAccess.notificationListenerEnabled
        val hasCamera = granted.any { it.contains("CAMERA") }
        val hasMic = granted.any { it.contains("RECORD_AUDIO") }
        val hasLocation = granted.any { it.contains("LOCATION") }
        val hasContacts = granted.any { it.contains("CONTACTS") }
        val isSideloaded = app.installerSource == "Sideloaded/Unknown" ||
                app.installerSource == "null" || app.installerSource.isEmpty()

        if (hasSms && hasInternet && hasBoot && isSideloaded && !app.isSystemApp) {
            bonus += 20
            ruleNames.add("SMS+Internet+Boot+Sideload")
            reasons.add(RiskReason("COMBO: SMS + Internet + Boot persistence from sideloaded source — classic banking trojan pattern", "CRITICAL"))
        }

        if (hasAccessibility && hasOverlay && !isAccessibilityTool) {
            bonus += 25
            ruleNames.add("Accessibility+Overlay")
            reasons.add(RiskReason("COMBO: Accessibility + Screen Overlay — can draw fake login screens (overlay attack)", "CRITICAL"))
        }

        if (hasDeviceAdmin && !isSecurityApp && !app.isSystemApp) {
            bonus += 20
            ruleNames.add("DeviceAdmin on non-security app")
            reasons.add(RiskReason("COMBO: Device Administrator on non-security app — may block uninstallation", "CRITICAL"))
        }

        if (hasNotifListener && hasSms) {
            bonus += 15
            ruleNames.add("NotifListener+SMS")
            reasons.add(RiskReason("COMBO: Notification Listener + SMS — dual OTP capture channels", "CRITICAL"))
        }

        if (hasCamera && hasMic && hasBoot && hasInternet && !app.isSystemApp) {
            bonus += 18
            ruleNames.add("Camera+Mic+Background+Net")
            reasons.add(RiskReason("COMBO: Camera + Mic + Background + Internet — potential covert surveillance", "CRITICAL"))
        }

        if (hasAccessibility && !isAccessibilityTool && !app.isSystemApp) {
            bonus += 15
            ruleNames.add("Unjustified accessibility")
            reasons.add(RiskReason("Accessibility service on non-accessibility app — suspicious", "HIGH"))
        }

        if (hasLocation && hasContacts && hasInternet && !app.isSystemApp) {
            bonus += 12
            ruleNames.add("Location+Contacts+Net")
            reasons.add(RiskReason("COMBO: Location + Contacts + Internet — can share location and social graph", "HIGH"))
        }

        return bonus to ruleNames
    }

    private fun detectContextMismatches(app: AppInfo, reasons: MutableList<RiskReason>): List<String> {
        val mismatches = mutableListOf<String>()
        val label = app.label.lowercase()
        val pkg = app.packageName.lowercase()
        val granted = app.grantedPermissions.filter { it.granted }.map { it.name }

        val torchKeywords = listOf("torch", "flashlight", "lamp")
        val calcKeywords = listOf("calculator", "calc")
        val wallpaperKeywords = listOf("wallpaper", "background", "theme")
        val utilityKeywords = listOf("cleaner", " booster", "speed", "ram", "optimizer")

        val isTorch = torchKeywords.any { label.contains(it) || pkg.contains(it) }
        val isCalc = calcKeywords.any { label.contains(it) || pkg.contains(it) }
        val isWallpaper = wallpaperKeywords.any { label.contains(it) || pkg.contains(it) }
        val isUtility = utilityKeywords.any { label.contains(it) || pkg.contains(it) }
        val isSimpleApp = isTorch || isCalc || isWallpaper || isUtility

        if (isSimpleApp) {
            val appType = when {
                isTorch -> "torch"
                isCalc -> "calculator"
                isWallpaper -> "wallpaper"
                else -> "utility"
            }
            if (granted.any { it.contains("SMS") }) {
                mismatches.add("Context mismatch: $appType app with SMS access")
                reasons.add(RiskReason("Context mismatch: A $appType app should not need SMS access", "HIGH"))
            }
            if (granted.any { it.contains("CONTACTS") }) {
                mismatches.add("Context mismatch: $appType app with contacts access")
                reasons.add(RiskReason("Context mismatch: A $appType app should not need contacts access", "HIGH"))
            }
            if (granted.any { it.contains("CALL_LOG") }) {
                mismatches.add("Context mismatch: $appType app with call log access")
                reasons.add(RiskReason("Context mismatch: A $appType app should not need call log access", "HIGH"))
            }
            if (app.specialAccess.accessibilityServiceEnabled) {
                mismatches.add("Context mismatch: $appType app with accessibility service")
                reasons.add(RiskReason("Context mismatch: A $appType app should not have an accessibility service", "CRITICAL"))
            }
        }

        return mismatches
    }

    private fun isTrustedApp(app: AppInfo): Boolean {
        val pkg = app.packageName.lowercase()
        return TRUSTED_PREFIXES.any { pkg.startsWith(it) } || app.isSystemApp
    }

    private fun isSecurityApp(app: AppInfo): Boolean {
        val label = app.label.lowercase()
        val pkg = app.packageName.lowercase()
        return SECURITY_APPS.any { label.contains(it) || pkg.contains(it) }
    }

    private fun isAccessibilityTool(app: AppInfo): Boolean {
        val label = app.label.lowercase()
        val pkg = app.packageName.lowercase()
        return ACCESSIBILITY_TOOLS.any { label.contains(it) || pkg.contains(it) }
    }
}
