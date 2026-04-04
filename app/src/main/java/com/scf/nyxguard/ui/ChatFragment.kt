package com.scf.nyxguard.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.scf.nyxguard.R
import com.scf.nyxguard.chat.ChatAdapter
import com.scf.nyxguard.chat.ChatMessage
import com.scf.nyxguard.chat.LocalAIResponder
import com.scf.nyxguard.common.ActiveTripStore
import com.scf.nyxguard.databinding.FragmentChatBinding
import com.scf.nyxguard.network.ApiClient
import com.scf.nyxguard.network.enqueue
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private val chatAdapter = ChatAdapter()
    private var activeTripId: Int? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.chatRecycler.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.chatRecycler.adapter = chatAdapter

        binding.btnSend.setOnClickListener { sendMessage() }
        binding.inputMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); true } else false
        }
        binding.btnMic.setOnClickListener {
            binding.inputMessage.setText(getString(R.string.chat_voice_placeholder_short))
            binding.inputMessage.setSelection(binding.inputMessage.text?.length ?: 0)
        }
        binding.chatSubtitle.text = getString(R.string.chat_status_online)
        loadHistory()
    }

    private fun sendMessage() {
        val text = binding.inputMessage.text?.toString()?.trim() ?: return
        if (text.isEmpty()) return

        chatAdapter.addMessage(ChatMessage(content = text, isUser = true))
        binding.inputMessage.text?.clear()
        chatAdapter.addMessage(
            ChatMessage(
                content = getString(R.string.chat_typing_placeholder),
                isUser = false,
                isPending = true,
                messageType = "typing"
            )
        )
        scrollToBottom()

        ApiClient.service.createChatMessage(
            com.scf.nyxguard.network.V2ChatMessageRequest(
                content = text,
                trip_id = activeTripId
            )
        )
            .enqueue(
                onSuccess = { response ->
                    if (_binding == null) return@enqueue
                    chatAdapter.removePendingAssistant()
                    chatAdapter.addMessage(response.assistant_message.toUiMessage())
                    scrollToBottom()
                },
                onError = {
                    if (_binding == null) return@enqueue
                    chatAdapter.removePendingAssistant()
                    val reply = LocalAIResponder.getResponse(text)
                    chatAdapter.addMessage(ChatMessage(content = reply, isUser = false))
                    scrollToBottom()
                }
            )
    }

    private fun loadHistory() {
        activeTripId = ActiveTripStore.get(requireContext())?.tripId?.takeIf { it > 0 }
        ApiClient.service.getChatMessages(activeTripId).enqueue(
            onSuccess = { response ->
                if (_binding == null) return@enqueue
                val messages = response.messages.map { it.toUiMessage() }
                if (messages.isEmpty()) {
                    chatAdapter.submitMessages(
                        listOf(ChatMessage(content = LocalAIResponder.getWelcomeMessage(), isUser = false))
                    )
                } else {
                    chatAdapter.submitMessages(messages)
                }
                scrollToBottom()
            },
            onError = {
                if (_binding == null) return@enqueue
                chatAdapter.submitMessages(
                    listOf(ChatMessage(content = LocalAIResponder.getWelcomeMessage(), isUser = false))
                )
                scrollToBottom()
            }
        )
    }

    private fun scrollToBottom() {
        if (chatAdapter.itemCount > 0) {
            binding.chatRecycler.scrollToPosition(chatAdapter.itemCount - 1)
        }
    }

    private fun com.scf.nyxguard.network.ChatMessageDto.toUiMessage(): ChatMessage {
        return ChatMessage(
            id = id,
            content = content,
            isUser = role == "user",
            timestamp = parseTimestamp(created_at),
            messageType = message_type
        )
    }

    private fun parseTimestamp(value: String): Long {
        return runCatching {
            OffsetDateTime.parse(value).toInstant().toEpochMilli()
        }.getOrElse { System.currentTimeMillis() }
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
