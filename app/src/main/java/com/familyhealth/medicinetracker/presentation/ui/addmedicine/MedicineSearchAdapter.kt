package com.familyhealth.medicinetracker.presentation.ui.addmedicine

import android.view.*
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.familyhealth.medicinetracker.R
import com.familyhealth.medicinetracker.domain.model.MedicineCatalog

class MedicineSearchAdapter(
    private val onSelected: (MedicineCatalog) -> Unit
) : ListAdapter<MedicineCatalog, MedicineSearchAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView     = view.findViewById(R.id.tv_catalog_name)
        val tvGeneric: TextView  = view.findViewById(R.id.tv_catalog_generic)
        val tvCategory: TextView = view.findViewById(R.id.tv_catalog_category)
        val tvDosage: TextView   = view.findViewById(R.id.tv_catalog_dosage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_medicine_search, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.tvName.text = item.name
        holder.tvGeneric.text = item.genericName.ifBlank { item.name }
        holder.tvCategory.text = item.category
        holder.tvDosage.text = item.defaultDosage
        holder.itemView.setOnClickListener { onSelected(item) }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<MedicineCatalog>() {
            override fun areItemsTheSame(a: MedicineCatalog, b: MedicineCatalog) = a.id == b.id
            override fun areContentsTheSame(a: MedicineCatalog, b: MedicineCatalog) = a == b
        }
    }
}
