package com.scf.nyxguard.walk

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.amap.api.services.help.Tip
import com.scf.nyxguard.R

class PoiSearchAdapter(
    private val onItemClick: (Tip) -> Unit
) : RecyclerView.Adapter<PoiSearchAdapter.ViewHolder>() {

    private val items = mutableListOf<Tip>()

    fun submitList(tips: List<Tip>) {
        items.clear()
        items.addAll(tips.filter { it.point != null })
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_poi_suggestion, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tip = items[position]
        holder.nameText.text = tip.name
        holder.addressText.text = buildString {
            if (!tip.district.isNullOrEmpty()) append(tip.district)
            if (!tip.address.isNullOrEmpty()) {
                if (isNotEmpty()) append(" ")
                append(tip.address)
            }
        }
        holder.itemView.setOnClickListener { onItemClick(tip) }
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.poi_name)
        val addressText: TextView = view.findViewById(R.id.poi_address)
    }
}
