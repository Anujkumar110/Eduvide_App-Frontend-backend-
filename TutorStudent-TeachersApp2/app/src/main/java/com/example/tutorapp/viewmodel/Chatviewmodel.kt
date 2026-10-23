package com.example.tutorapp.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tutorapp.network.ChatMessageData
import com.example.tutorapp.network.RetrofitClient
import com.example.tutorapp.network.TokenManager
import com.example.tutorapp.websocket.SignalingClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatMessageUI(
    val id: String? = null,
    val text: String,
    val isSent: Boolean,
    val timestamp: String? = null,
    val senderId: String = ""
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val _messages = MutableStateFlow<List<ChatMessageUI>>(emptyList())
    val messages: StateFlow<List<ChatMessageUI>> = _messages.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private var signalingClient: SignalingClient? = null
    private var currentUserId: String = ""
    private var otherUserId: String = ""
    private var conversationId: String = ""

    fun initChat(
        roomId: String,
        contactUserId: String,
        contactName: String
    ) {
        val context = getApplication<Application>()
        currentUserId = TokenManager.getUserId(context) ?: ""
        otherUserId = contactUserId
        conversationId = roomId

        val token = TokenManager.getToken(context) ?: ""
        val cleanToken = token.removePrefix("Bearer ").trim()

        Log.d("ChatViewModel", "initChat: roomId=$roomId, contact=$contactName, userId=$currentUserId")

        val client = SignalingClient(roomId)
        signalingClient = client

        client.onConnected = {
            _isConnected.value = true
            Log.d("ChatViewModel", "WebSocket connected")
        }

        client.onDisconnected = {
            _isConnected.value = false
            Log.d("ChatViewModel", "WebSocket disconnected")
        }

        client.onChatMessage = { sender, text ->
            // Message is from other user (not me)
            val isMe = (sender == currentUserId)
            _messages.value = _messages.value + ChatMessageUI(
                text = text,
                isSent = isMe,  // isSent = true means it's MY message
                senderId = sender
            )
            Log.d("ChatViewModel", "Received message from $sender: $text, isMe=$isMe")
        }

        client.connect(cleanToken)

        // Load message history
        loadMessageHistory()
    }

    private fun loadMessageHistory() {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val token = TokenManager.getToken(context) ?: ""
                val response = RetrofitClient.api.getChatMessages(
                    token = token,
                    conversationId = conversationId
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    val history = response.body()!!.data
                    val uiMessages = history.map { msg ->
                        ChatMessageUI(
                            id = msg.id,
                            text = msg.content,
                            isSent = (msg.senderId == currentUserId),
                            timestamp = msg.createdAt,
                            senderId = msg.senderId
                        )
                    }
                    _messages.value = uiMessages
                    Log.d("ChatViewModel", "Loaded ${uiMessages.size} messages from history")
                } else {
                    Log.w("ChatViewModel", "Failed to load message history: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error loading message history: ${e.message}", e)
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val context = getApplication<Application>()
        val sender = currentUserId.ifBlank { TokenManager.getUserName(context) }
        signalingClient?.sendChat(sender, text)

        _messages.value = _messages.value + ChatMessageUI(
            text = text,
            isSent = true,
            senderId = sender
        )

        Log.d("ChatViewModel", "Sent message: $text")
    }

    fun sendTyping(isTyping: Boolean) {
        if (_isTyping.value != isTyping) {
            _isTyping.value = isTyping
            signalingClient?.sendTyping(otherUserId, isTyping)
        }
    }

    fun markAsRead() {
        val context = getApplication<Application>()
        val token = TokenManager.getToken(context) ?: ""
        signalingClient?.sendMarkAsRead(conversationId, currentUserId)
    }

    fun disconnect() {
        signalingClient?.disconnect()
        signalingClient = null
        Log.d("ChatViewModel", "Chat disconnected")
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}
