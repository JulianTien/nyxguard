package com.scf.nyxguard.chat

data class ChatMessage(
    val id: Int = 0,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isPending: Boolean = false,
    val messageType: String = "chat"
)
