package com.scf.nyxguard.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.scf.nyxguard.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()
    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun submitMessages(items: List<ChatMessage>) {
        messages.clear()
        messages.addAll(items)
        notifyDataSetChanged()
    }

    fun addMessage(msg: ChatMessage) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    fun removePendingAssistant() {
        val index = messages.indexOfLast { !it.isUser && it.isPending }
        if (index >= 0) {
            messages.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) TYPE_USER else TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = if (viewType == TYPE_USER)
            R.layout.item_chat_user else R.layout.item_chat_ai
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = messages[position]
        holder.content.text = message.content
        holder.time.text = if (message.isPending) {
            "..."
        } else {
            timeFormatter.format(Date(message.timestamp))
        }
        holder.content.alpha = if (message.isPending) 0.7f else 1f
    }

    override fun getItemCount() = messages.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val content: TextView = view.findViewById(R.id.message_text)
        val time: TextView = view.findViewById(R.id.message_time)
    }

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_AI = 1
    }
}
