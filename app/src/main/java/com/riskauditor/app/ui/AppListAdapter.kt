package com.riskauditor.app.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.riskauditor.app.R
import com.riskauditor.app.model.AppInfo
import com.riskauditor.app.model.RiskBand
import com.riskauditor.app.model.RiskResult

class AppListAdapter(
    private val onClick: (AppInfo, RiskResult) -> Unit,
    private val onLongClick: (AppInfo, RiskResult) -> Unit = { _, _ -> }
) : ListAdapter<Pair<AppInfo, RiskResult>, AppListAdapter.ViewHolder>(DIFF) {

    var safePackages: Set<String> = emptySet()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (app, risk) = getItem(position)
        holder.bind(app, risk)
        holder.itemView.setOnClickListener { onClick(app, risk) }
        holder.itemView.setOnLongClickListener {
            onLongClick(app, risk)
            true
        }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivIcon: ImageView = view.findViewById(R.id.ivIcon)
        private val tvLabel: TextView = view.findViewById(R.id.tvLabel)
        private val tvPackage: TextView = view.findViewById(R.id.tvPackage)
        private val tvScore: TextView = view.findViewById(R.id.tvScore)
        private val tvBand: TextView = view.findViewById(R.id.tvBand)
        private val tvReasonCount: TextView = view.findViewById(R.id.tvReasonCount)
        private val tvPermCount: TextView = view.findViewById(R.id.tvPermCount)
        private val tvSource: TextView = view.findViewById(R.id.tvSource)
        private val ivSafe: ImageView = view.findViewById(R.id.ivSafe)

        fun bind(app: AppInfo, risk: RiskResult) {
            app.icon?.let { ivIcon.setImageDrawable(it) }
                ?: ivIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            tvLabel.text = app.label
            tvPackage.text = app.packageName
            tvScore.text = "${risk.score}"
            tvBand.text = risk.band.label

            // Permission count
            val permCount = app.grantedPermissions.size
            tvPermCount.text = if (permCount == 0) "No perms" else "$permCount perms"

            // Install source badge
            val isPlayStore = app.installerSource.contains("com.android.vending", ignoreCase = true)
            if (!app.isSystemApp) {
                tvSource.visibility = View.VISIBLE
                tvSource.text = if (isPlayStore) "Play Store" else "Sideloaded"
                tvSource.setTextColor(
                    if (isPlayStore) Color.parseColor("#CDEAC0")
                    else Color.parseColor("#FFDDB3")
                )
            } else {
                tvSource.visibility = View.GONE
            }

            // Safe app indicator
            ivSafe.visibility = if (safePackages.contains(app.packageName)) View.VISIBLE else View.GONE

            // Material 3 style colors
            val (bandColor, bgColor) = when (risk.band) {
                RiskBand.LOW -> Color.parseColor("#CDEAC0") to Color.parseColor("#1A2C1E")
                RiskBand.MEDIUM -> Color.parseColor("#FFDDB3") to Color.parseColor("#2C2318")
                RiskBand.HIGH -> Color.parseColor("#F2B8B5") to Color.parseColor("#2C1B1E")
                RiskBand.CRITICAL -> Color.parseColor("#F2B8B5") to Color.parseColor("#2C1B24")
            }

            tvScore.setTextColor(bandColor)
            tvBand.setTextColor(bandColor)
            (tvScore.parent as? View)?.setBackgroundColor(bgColor)

            val reasonCount = risk.reasons.size
            tvReasonCount.text = when {
                reasonCount == 0 -> "No issues found"
                reasonCount == 1 -> "1 risk indicator"
                else -> "$reasonCount risk indicators"
            }
            tvReasonCount.setTextColor(
                if (reasonCount > 2) Color.parseColor("#F2B8B5")
                else if (reasonCount > 0) Color.parseColor("#FFDDB3")
                else Color.parseColor("#CDEAC0")
            )
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Pair<AppInfo, RiskResult>>() {
            override fun areItemsTheSame(
                a: Pair<AppInfo, RiskResult>,
                b: Pair<AppInfo, RiskResult>
            ) = a.first.packageName == b.first.packageName

            override fun areContentsTheSame(
                a: Pair<AppInfo, RiskResult>,
                b: Pair<AppInfo, RiskResult>
            ) = a.first.packageName == b.first.packageName && a.second.score == b.second.score
        }
    }
}
