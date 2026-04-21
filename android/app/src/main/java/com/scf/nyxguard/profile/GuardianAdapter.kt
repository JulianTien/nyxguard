package com.scf.nyxguard.profile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.scf.nyxguard.R

class GuardianAdapter(
    private val onDelete: (Guardian) -> Unit
) : RecyclerView.Adapter<GuardianAdapter.ViewHolder>() {

    private val items = mutableListOf<Guardian>()

    fun submitList(list: List<Guardian>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_guardian, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val g = items[position]
        holder.name.text = g.name
        holder.phone.text = g.phone
        holder.relationship.text = g.relationship
        holder.btnDelete.setOnClickListener { onDelete(g) }
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.guardian_name)
        val phone: TextView = view.findViewById(R.id.guardian_phone)
        val relationship: Chip = view.findViewById(R.id.guardian_relationship)
        val btnDelete: ImageView = view.findViewById(R.id.btn_delete)
    }
}
