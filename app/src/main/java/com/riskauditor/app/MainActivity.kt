package com.riskauditor.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.riskauditor.app.model.AppInfo
import com.riskauditor.app.model.DeviceScanResult
import com.riskauditor.app.model.RiskBand
import com.riskauditor.app.model.RiskResult
import com.riskauditor.app.risk.RiskScorer
import com.riskauditor.app.scan.AppScanner
import com.riskauditor.app.ui.AppListAdapter
import com.riskauditor.app.util.SafeAppsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var rvApps: RecyclerView
    private lateinit var progressScan: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var tvSummary: TextView
    private lateinit var tvCritical: TextView
    private lateinit var tvHigh: TextView
    private lateinit var tvMedium: TextView
    private lateinit var tvLow: TextView
    private lateinit var switchSystem: SwitchMaterial
    private lateinit var btnRescan: MaterialButton
    private lateinit var etSearch: TextInputEditText
    private lateinit var btnSort: ImageButton
    private lateinit var chipGroupPerms: ChipGroup
    private lateinit var adapter: AppListAdapter

    private var allResults: List<Pair<AppInfo, RiskResult>> = emptyList()
    private var filteredResults: List<Pair<AppInfo, RiskResult>> = emptyList()
    private var includeSystem = false
    private var searchQuery = ""
    private var currentSortMode = SortMode.RISK_DESC
    private var currentPermFilter: String = "all"

    enum class SortMode(val label: String) {
        RISK_DESC("Risk: High to Low"),
        RISK_ASC("Risk: Low to High"),
        NAME_ASC("Name: A to Z"),
        NAME_DESC("Name: Z to A"),
        PERMS_DESC("Most Permissions"),
        NEWEST("Recently Updated")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rvApps = findViewById(R.id.rvApps)
        progressScan = findViewById(R.id.progressScan)
        tvStatus = findViewById(R.id.tvStatus)
        tvSummary = findViewById(R.id.tvSummary)
        tvCritical = findViewById(R.id.tvCritical)
        tvHigh = findViewById(R.id.tvHigh)
        tvMedium = findViewById(R.id.tvMedium)
        tvLow = findViewById(R.id.tvLow)
        switchSystem = findViewById(R.id.switchSystem)
        btnRescan = findViewById(R.id.btnRescan)
        etSearch = findViewById(R.id.etSearch)
        btnSort = findViewById(R.id.btnSort)
        chipGroupPerms = findViewById(R.id.chipGroupPerms)

        adapter = AppListAdapter(
            onClick = { app, risk ->
                val intent = Intent(this, com.riskauditor.app.ui.AppDetailActivity::class.java).apply {
                    putExtra("package_name", app.packageName)
                }
                startActivity(intent)
            },
            onLongClick = { app, risk ->
                showAppActionsDialog(app, risk)
            }
        )
        rvApps.layoutManager = LinearLayoutManager(this)
        rvApps.adapter = adapter

        switchSystem.setOnCheckedChangeListener { _, isChecked ->
            includeSystem = isChecked
            startScan()
        }

        btnRescan.setOnClickListener { startScan() }
        btnSort.setOnClickListener { showSortDialog() }

        chipGroupPerms.setOnCheckedStateChangeListener { _, checkedIds ->
            currentPermFilter = when {
                checkedIds.isEmpty() || checkedIds.contains(R.id.chipAll) -> "all"
                checkedIds.contains(R.id.chipCamera) -> "camera"
                checkedIds.contains(R.id.chipLocation) -> "location"
                checkedIds.contains(R.id.chipMic) -> "mic"
                checkedIds.contains(R.id.chipSMS) -> "sms"
                checkedIds.contains(R.id.chipContacts) -> "contacts"
                checkedIds.contains(R.id.chipPhone) -> "phone"
                checkedIds.contains(R.id.chipStorage) -> "storage"
                else -> "all"
            }
            applyFilters()
        }

        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                searchQuery = s?.toString()?.lowercase() ?: ""
                applyFilters()
            }
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }

        startScan()
    }

    private fun startScan() {
        progressScan.visibility = View.VISIBLE
        tvStatus.visibility = View.VISIBLE
        rvApps.visibility = View.GONE
        tvStatus.text = "Scanning installed apps..."

        lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                val startTime = System.currentTimeMillis()
                val scanner = AppScanner(this@MainActivity)
                val scorer = RiskScorer()
                val apps = scanner.scanAll(includeSystem)
                val safeApps = SafeAppsManager.getSafeApps(this@MainActivity)
                val scored = apps
                    .filter { it.packageName !in safeApps }
                    .map { it to scorer.score(it) }
                    .sortedByDescending { it.second.score }
                val endTime = System.currentTimeMillis()

                val critical = scored.count { it.second.band == RiskBand.CRITICAL }
                val high = scored.count { it.second.band == RiskBand.HIGH }
                val medium = scored.count { it.second.band == RiskBand.MEDIUM }
                val low = scored.count { it.second.band == RiskBand.LOW }

                DeviceScanResult(
                    apps = scored,
                    totalUserApps = apps.count { !it.isSystemApp },
                    totalSystemApps = apps.count { it.isSystemApp },
                    criticalCount = critical,
                    highCount = high,
                    mediumCount = medium,
                    lowCount = low,
                    scanTimeMs = endTime - startTime
                )
            }

            allResults = result.apps
            adapter.safePackages = SafeAppsManager.getSafeApps(this@MainActivity)
            applyFilters()

            tvSummary.text = "Scanned ${result.totalUserApps} user apps" +
                    (if (includeSystem) " + ${result.totalSystemApps} system apps" else "") +
                    " in ${result.scanTimeMs}ms"

            tvCritical.text = "${result.criticalCount}"
            tvHigh.text = "${result.highCount}"
            tvMedium.text = "${result.mediumCount}"
            tvLow.text = "${result.lowCount}"

            progressScan.visibility = View.GONE
            tvStatus.visibility = View.GONE
            rvApps.visibility = View.VISIBLE
        }
    }

    private fun applyFilters() {
        var list = allResults

        // Apply search filter
        if (searchQuery.isNotEmpty()) {
            list = list.filter { (app, _) ->
                app.label.lowercase().contains(searchQuery) ||
                        app.packageName.lowercase().contains(searchQuery)
            }
        }

        // Apply permission filter
        if (currentPermFilter != "all") {
            list = list.filter { (app, _) ->
                val permNames = app.grantedPermissions.map { it.name }
                when (currentPermFilter) {
                    "camera" -> permNames.any { it.contains("CAMERA") }
                    "location" -> permNames.any { it.contains("LOCATION") }
                    "mic" -> permNames.any { it.contains("RECORD_AUDIO") }
                    "sms" -> permNames.any { it.contains("SMS") || it.contains("MMS") }
                    "contacts" -> permNames.any { it.contains("CONTACTS") || it.contains("CALL_LOG") }
                    "phone" -> permNames.any {
                        it.contains("PHONE_STATE") || it.contains("CALL_PHONE") ||
                                it.contains("READ_PHONE_NUMBERS")
                    }
                    "storage" -> permNames.any {
                        it.contains("EXTERNAL_STORAGE") || it.contains("READ_MEDIA") ||
                                it.contains("WRITE_EXTERNAL")
                    }
                    else -> true
                }
            }
        }

        // Apply sort
        list = when (currentSortMode) {
            SortMode.RISK_DESC -> list.sortedByDescending { it.second.score }
            SortMode.RISK_ASC -> list.sortedBy { it.second.score }
            SortMode.NAME_ASC -> list.sortedBy { it.first.label.lowercase() }
            SortMode.NAME_DESC -> list.sortedByDescending { it.first.label.lowercase() }
            SortMode.PERMS_DESC -> list.sortedByDescending { it.first.grantedPermissions.size }
            SortMode.NEWEST -> list.sortedByDescending { it.first.updateTime }
        }

        filteredResults = list
        adapter.submitList(list)

        // Update summary with filter info
        if (currentPermFilter != "all" || searchQuery.isNotEmpty()) {
            tvSummary.text = "Showing ${list.size} of ${allResults.size} apps"
        }
    }

    private fun showSortDialog() {
        val options = SortMode.entries.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Sort by")
            .setItems(options) { _, which ->
                currentSortMode = SortMode.entries[which]
                applyFilters()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAppActionsDialog(app: AppInfo, risk: RiskResult) {
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        // Mark as safe/unsafe
        val isSafe = SafeAppsManager.isSafe(this, app.packageName)
        options.add(if (isSafe) "Remove from Safe Apps" else "Mark as Safe App")
        actions.add {
            SafeAppsManager.toggleSafe(this, app.packageName)
            Toast.makeText(
                this,
                if (!isSafe) "${app.label} marked as safe" else "${app.label} removed from safe apps",
                Toast.LENGTH_SHORT
            ).show()
            startScan()
        }

        // Share report
        options.add("Share Risk Report")
        actions.add { shareAppReport(app, risk) }

        // Open settings
        options.add("Open App Settings")
        actions.add { openAppSettings(app.packageName) }

        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(options.toTypedArray()) { _, which ->
                actions[which]()
            }
            .setNegativeButton("Cancel", null)
            .show()
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

    private fun openAppSettings(packageName: String) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_share -> {
                shareScanSummary()
                true
            }
            R.id.action_safe_apps -> {
                showSafeAppsInfo()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun shareScanSummary() {
        val report = buildString {
            appendLine("=== Device Risk Scan Summary ===")
            appendLine("Total Apps Scanned: ${allResults.size}")
            appendLine("")
            appendLine("Risk Distribution:")
            appendLine("  Critical: ${allResults.count { it.second.band == RiskBand.CRITICAL }}")
            appendLine("  High: ${allResults.count { it.second.band == RiskBand.HIGH }}")
            appendLine("  Medium: ${allResults.count { it.second.band == RiskBand.MEDIUM }}")
            appendLine("  Low: ${allResults.count { it.second.band == RiskBand.LOW }}")
            appendLine("")
            if (allResults.any { it.second.band == RiskBand.HIGH || it.second.band == RiskBand.CRITICAL }) {
                appendLine("Top Risky Apps:")
                allResults
                    .filter { it.second.band == RiskBand.HIGH || it.second.band == RiskBand.CRITICAL }
                    .take(5)
                    .forEach { (app, risk) ->
                        appendLine("  ${app.label}: ${risk.score}/100 (${risk.band.label})")
                    }
            }
            appendLine("")
            appendLine("Generated by App Risk Auditor")
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Device Risk Scan Summary")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        startActivity(Intent.createChooser(intent, "Share Scan Summary"))
    }

    private fun showSafeAppsInfo() {
        val safeApps = SafeAppsManager.getSafeApps(this)
        AlertDialog.Builder(this)
            .setTitle("Safe Apps (${safeApps.size})")
            .setMessage(
                if (safeApps.isEmpty()) {
                    "No apps marked as safe.\n\nLong-press any app in the list to mark it as safe."
                } else {
                    "These apps are excluded from risk scanning:\n\n" +
                            safeApps.joinToString("\n") { "• $it" }
                }
            )
            .setPositiveButton("OK", null)
            .show()
    }
}
