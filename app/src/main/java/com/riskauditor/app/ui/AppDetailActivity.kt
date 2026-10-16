package com.riskauditor.app.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.riskauditor.app.R
import com.riskauditor.app.model.AppInfo
import com.riskauditor.app.model.PermissionCategory
import com.riskauditor.app.model.RiskBand
import com.riskauditor.app.model.RiskResult
import com.riskauditor.app.risk.RiskScorer
import com.riskauditor.app.scan.AppScanner
import com.riskauditor.app.util.SafeAppsManager
import java.text.SimpleDateFormat
import java.util.*

class AppDetailActivity : AppCompatActivity() {

    private lateinit var scanner: AppScanner
    private lateinit var scorer: RiskScorer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_detail)

        scanner = AppScanner(this)
        scorer = RiskScorer()

        val packageName = intent.getStringExtra("package_name") ?: run { finish(); return }

        val apps = scanner.scanAll(includeSystemApps = true)
        val app = apps.find { it.packageName == packageName } ?: run {
            Toast.makeText(this, "App not found", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        val risk = scorer.score(app)

        setupHeader(app, risk)
        setupWhyFlagged(risk)
        setupCategoryBreakdown(app, risk)
        setupSpecialAccess(app)
        setupActions(app, risk)
    }

    private fun setupHeader(app: AppInfo, risk: RiskResult) {
        val ivIcon = findViewById<ImageView>(R.id.ivIcon)
        val tvLabel = findViewById<TextView>(R.id.tvLabel)
        val tvPackage = findViewById<TextView>(R.id.tvPackage)
        val tvScore = findViewById<TextView>(R.id.tvScore)
        val tvBand = findViewById<TextView>(R.id.tvBand)
        val tvMeta = findViewById<TextView>(R.id.tvMeta)

        app.icon?.let { ivIcon.setImageDrawable(it) }
        tvLabel.text = app.label
        tvPackage.text = app.packageName
        tvScore.text = "${risk.score}"
        tvBand.text = risk.band.label

        // Material 3 colors
        val (bandColor, bgColor) = when (risk.band) {
            RiskBand.LOW -> Color.parseColor("#CDEAC0") to Color.parseColor("#1A2C1E")
            RiskBand.MEDIUM -> Color.parseColor("#FFDDB3") to Color.parseColor("#2C2318")
            RiskBand.HIGH -> Color.parseColor("#F2B8B5") to Color.parseColor("#2C1B1E")
            RiskBand.CRITICAL -> Color.parseColor("#F2B8B5") to Color.parseColor("#2C1B24")
        }
        tvScore.setTextColor(bandColor)
        tvBand.setTextColor(bandColor)
        (tvScore.parent as? android.view.View)?.setBackgroundColor(bgColor)

        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        tvMeta.text = buildString {
            append("v${app.versionName}")
            append("  ·  Installed ${sdf.format(Date(app.installTime))}")
            append("  ·  ${app.installerSource}")
            append("  ·  SDK ${app.targetSdk}")
        }
    }

    private fun setupWhyFlagged(risk: RiskResult) {
        val container = findViewById<LinearLayout>(R.id.containerReasons)
        container.removeAllViews()

        if (risk.reasons.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "No significant risk indicators found for this app."
                setTextColor(Color.parseColor("#CDEAC0"))
                textSize = 14f
                setPadding(0, 4, 0, 4)
            })
            return
        }

        risk.reasons.forEach { reason ->
            val severityColor = when (reason.severity) {
                "CRITICAL" -> Color.parseColor("#F2B8B5")
                "HIGH" -> Color.parseColor("#F2B8B5")
                "MEDIUM" -> Color.parseColor("#FFDDB3")
                else -> Color.parseColor("#CDEAC0")
            }

            val severityLabel = when (reason.severity) {
                "CRITICAL" -> "CRIT"
                "HIGH" -> "HIGH"
                "MEDIUM" -> "MED"
                else -> "LOW"
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 6, 0, 6)
                gravity = Gravity.TOP
            }

            val badge = TextView(this).apply {
                text = severityLabel
                setTextColor(Color.parseColor("#121218"))
                textSize = 9f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setBackgroundColor(severityColor)
                setPadding(8, 3, 8, 3)
                minWidth = 42
            }
            row.addView(badge)

            val text = TextView(this).apply {
                text = reason.text
                setTextColor(Color.parseColor("#CAC4D0"))
                textSize = 14f
                gravity = Gravity.TOP
                setPadding(12, 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(text)

            container.addView(row)
        }
    }

    private fun setupCategoryBreakdown(app: AppInfo, risk: RiskResult) {
        val container = findViewById<LinearLayout>(R.id.containerCategories)
        container.removeAllViews()

        PermissionCategory.entries.forEach { cat ->
            val score = risk.categoryScores[cat] ?: 0
            val grantedPerms = app.categoryPermissions[cat]?.filter { it.granted } ?: emptyList()
            if (score == 0 && grantedPerms.isEmpty()) return@forEach

            val permNames = grantedPerms.joinToString("\n") { "· " + it.name.substringAfterLast(".") }
                .ifEmpty { "Declared but not granted" }

            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 10, 0, 10)
            }

            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val catName = TextView(this).apply {
                text = cat.displayName
                textSize = 14f
                setTextColor(Color.parseColor("#E6E1E5"))
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            header.addView(catName)

            val scoreBadge = TextView(this).apply {
                text = "$score/${cat.weight}"
                textSize = 12f
                setTextColor(if (score > 0) Color.parseColor("#F2B8B5") else Color.parseColor("#938F9E"))
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setBackgroundColor(
                    if (score > 0) Color.parseColor("#2C1B24") else Color.parseColor("#25233A")
                )
                setPadding(10, 4, 10, 4)
            }
            header.addView(scoreBadge)

            layout.addView(header)

            val perms = TextView(this).apply {
                text = permNames
                textSize = 12f
                setTextColor(Color.parseColor("#938F9E"))
                setPadding(0, 6, 0, 0)
            }
            layout.addView(perms)

            container.addView(layout)
        }
    }

    private fun setupSpecialAccess(app: AppInfo) {
        val container = findViewById<LinearLayout>(R.id.containerSpecial)
        container.removeAllViews()
        val sa = app.specialAccess

        fun addRow(label: String, active: Boolean, detail: String = "") {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 6, 0, 6)
                gravity = Gravity.CENTER_VERTICAL
            }

            val dot = TextView(this).apply {
                text = if (active) "●" else "○"
                setTextColor(if (active) Color.parseColor("#F2B8B5") else Color.parseColor("#5C5866"))
                textSize = 14f
            }
            row.addView(dot)

            val labelText = TextView(this).apply {
                text = if (detail.isNotEmpty()) "$label — $detail" else label
                setTextColor(if (active) Color.parseColor("#F2B8B5") else Color.parseColor("#938F9E"))
                textSize = 14f
                setPadding(12, 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(labelText)

            if (active) {
                val badge = TextView(this).apply {
                    text = "ACTIVE"
                    setTextColor(Color.parseColor("#F2B8B5"))
                    textSize = 9f
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setBackgroundColor(Color.parseColor("#2C1B24"))
                    setPadding(8, 3, 8, 3)
                }
                row.addView(badge)
            }

            container.addView(row)
        }

        addRow("Accessibility Service", sa.accessibilityServiceEnabled)
        addRow("Device Administrator", sa.deviceAdminActive)
        addRow("Notification Listener", sa.notificationListenerEnabled)
        addRow("Screen Overlay", sa.canDrawOverlays)
        addRow("Install Packages", sa.requestInstallPackages)
        addRow("Usage Stats", sa.packageUsageStats)
        addRow("VPN Service", sa.vpnService)
        addRow("Notification Policy", sa.notificationPolicyAccess)
    }

    private fun setupActions(app: AppInfo, risk: RiskResult) {
        val btnSettings = findViewById<MaterialButton>(R.id.btnOpenSettings)
        val btnUninstall = findViewById<MaterialButton>(R.id.btnUninstall)
        val btnShare = findViewById<MaterialButton>(R.id.btnShare)
        val switchSafe = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchSafe)

        btnSettings.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", app.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }

        btnUninstall.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Uninstall ${app.label}?")
                .setMessage("This will remove the app and its data from your device.")
                .setPositiveButton("Uninstall") { _, _ ->
                    val intent = Intent(Intent.ACTION_DELETE).apply {
                        data = Uri.fromParts("package", app.packageName, null)
                    }
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnShare.setOnClickListener { shareAppReport(app, risk) }

        // Safe toggle
        switchSafe.isChecked = SafeAppsManager.isSafe(this, app.packageName)
        switchSafe.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                SafeAppsManager.markSafe(this, app.packageName)
                Toast.makeText(this, "${app.label} marked as safe", Toast.LENGTH_SHORT).show()
            } else {
                SafeAppsManager.unmarkSafe(this, app.packageName)
                Toast.makeText(this, "${app.label} removed from safe apps", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareAppReport(app: AppInfo, risk: RiskResult) {
        val report = buildString {
            appendLine("=== App Risk Report ===")
            appendLine("App: ${app.label}")
            appendLine("Package: ${app.packageName}")
            appendLine("Version: ${app.versionName}")
            appendLine("Risk Score: ${risk.score}/100 (${risk.band.label})")
            appendLine("Install Source: ${app.installerSource}")
            appendLine("Target SDK: ${app.targetSdk}")
            appendLine("")
            appendLine("Permissions (${app.grantedPermissions.size} granted):")
            app.grantedPermissions.take(10).forEach { appendLine("  • ${it.name}") }
            if (app.grantedPermissions.size > 10) appendLine("  ... and ${app.grantedPermissions.size - 10} more")
            appendLine("")
            if (risk.reasons.isNotEmpty()) {
                appendLine("Risk Indicators:")
                risk.reasons.forEach { appendLine("  • ${it.text}") }
            }
            appendLine("")
            appendLine("Generated by App Risk Auditor")
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Risk Report: ${app.label}")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        startActivity(Intent.createChooser(intent, "Share Report"))
    }
}
