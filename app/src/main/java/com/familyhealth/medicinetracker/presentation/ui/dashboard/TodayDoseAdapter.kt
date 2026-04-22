package com.familyhealth.medicinetracker.presentation.ui.dashboard

import android.graphics.Color
import android.view.*
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.familyhealth.medicinetracker.R
import com.familyhealth.medicinetracker.domain.model.*
import java.text.SimpleDateFormat
import java.util.*

class TodayDoseAdapter(
    private val onTaken: (TodayDose) -> Unit,
    private val onSkipped: (TodayDose) -> Unit
) : ListAdapter<TodayDose, TodayDoseAdapter.ViewHolder>(DIFF_CALLBACK) {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMedName: TextView    = itemView.findViewById(R.id.tv_med_name)
        val tvDosage: TextView     = itemView.findViewById(R.id.tv_dosage)
        val tvTime: TextView       = itemView.findViewById(R.id.tv_scheduled_time)
        val tvStatus: TextView     = itemView.findViewById(R.id.tv_status)
        val tvFoodNote: TextView   = itemView.findViewById(R.id.tv_food_note)
        val btnTaken: Button       = itemView.findViewById(R.id.btn_taken)
        val btnSkip: Button        = itemView.findViewById(R.id.btn_skip)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_today_dose, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val med = item.medication
        val dose = item.doseLog

        holder.tvMedName.text = med.name
        holder.tvDosage.text = med.dosageAmount

        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        holder.tvTime.text = timeFormat.format(Date(dose.scheduledAt))

        holder.tvFoodNote.text = when (med.foodRelation) {
            FoodRelation.BEFORE_FOOD -> "Take before food"
            FoodRelation.AFTER_FOOD  -> "Take after food"
            FoodRelation.WITH_FOOD   -> "Take with food"
            FoodRelation.ANYTIME     -> ""
        }
        holder.tvFoodNote.visibility =
            if (med.foodRelation == FoodRelation.ANYTIME) View.GONE else View.VISIBLE

        // Status display
        when (dose.status) {
            DoseStatus.TAKEN -> {
                holder.tvStatus.text = "✅ Taken"
                holder.tvStatus.setTextColor(Color.parseColor("#4CAF50"))
                holder.btnTaken.visibility = View.GONE
                holder.btnSkip.visibility = View.GONE
                holder.itemView.alpha = 0.6f
            }
            DoseStatus.SKIPPED -> {
                holder.tvStatus.text = "⏭ Skipped"
                holder.tvStatus.setTextColor(Color.parseColor("#FF9800"))
                holder.btnTaken.visibility = View.GONE
                holder.btnSkip.visibility = View.GONE
                holder.itemView.alpha = 0.6f
            }
            DoseStatus.NAGGING -> {
                holder.tvStatus.text = "⏰ Due now"
                holder.tvStatus.setTextColor(Color.parseColor("#F44336"))
                holder.btnTaken.visibility = View.VISIBLE
                holder.btnSkip.visibility = View.VISIBLE
                holder.itemView.alpha = 1.0f
            }
            DoseStatus.SNOOZED -> {
                holder.tvStatus.text = "🔇 Muted (1hr)"
                holder.tvStatus.setTextColor(Color.parseColor("#9E9E9E"))
                holder.btnTaken.visibility = View.VISIBLE
                holder.btnSkip.visibility = View.VISIBLE
                holder.itemView.alpha = 1.0f
            }
            DoseStatus.SUPPRESSED -> {
                holder.tvStatus.text = "🚗 Driving — Take later"
                holder.tvStatus.setTextColor(Color.parseColor("#2196F3"))
                holder.btnTaken.visibility = View.VISIBLE
                holder.btnSkip.visibility = View.VISIBLE
                holder.itemView.alpha = 1.0f
            }
            else -> {
                holder.tvStatus.text = "🕐 Upcoming"
                holder.tvStatus.setTextColor(Color.parseColor("#607D8B"))
                holder.btnTaken.visibility = View.VISIBLE
                holder.btnSkip.visibility = View.GONE
                holder.itemView.alpha = 1.0f
            }
        }

        holder.btnTaken.setOnClickListener { onTaken(item) }
        holder.btnSkip.setOnClickListener { onSkipped(item) }
    }

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<TodayDose>() {
            override fun areItemsTheSame(oldItem: TodayDose, newItem: TodayDose) =
                oldItem.doseLog.id == newItem.doseLog.id
            override fun areContentsTheSame(oldItem: TodayDose, newItem: TodayDose) =
                oldItem.doseLog.status == newItem.doseLog.status &&
                oldItem.medication.totalStock == newItem.medication.totalStock
        }
    }
}
