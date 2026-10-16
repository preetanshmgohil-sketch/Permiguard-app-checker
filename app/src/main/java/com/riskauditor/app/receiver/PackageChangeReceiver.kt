package com.riskauditor.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.riskauditor.app.RiskAuditorApp
import com.riskauditor.app.risk.RiskScorer
import com.riskauditor.app.scan.AppScanner

class PackageChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart ?: return
        val action = intent.action ?: return

        if (packageName == context.packageName) return
        if (action != Intent.ACTION_PACKAGE_ADDED && action != Intent.ACTION_PACKAGE_REPLACED) return
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false) && action == Intent.ACTION_PACKAGE_ADDED) return

        try {
            val scanner = AppScanner(context)
            val scorer = RiskScorer()
            val flags = android.content.pm.PackageManager.GET_PERMISSIONS
            val pkg = context.packageManager.getPackageInfo(packageName, flags)
            val ai = pkg.applicationInfo ?: return
            val label = context.packageManager.getApplicationLabel(ai).toString()

            // Quick scan just this package
            val allApps = scanner.scanAll(includeSystemApps = false)
            val app = allApps.find { it.packageName == packageName } ?: return
            val risk = scorer.score(app)

            if (risk.band == com.riskauditor.app.model.RiskBand.HIGH ||
                risk.band == com.riskauditor.app.model.RiskBand.CRITICAL) {

                val notif = NotificationCompat.Builder(context, RiskAuditorApp.CHANNEL_HIGH_RISK)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("⚠ ${risk.band.label} risk app installed")
                    .setContentText("$label scored ${risk.score}/100. ${risk.reasons.firstOrNull()?.text ?: ""}")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .build()

                NotificationManagerCompat.from(context).notify(packageName.hashCode(), notif)
            }
        } catch (e: Exception) {
            // Silent fail — receiver should never crash the host
        }
    }
}
