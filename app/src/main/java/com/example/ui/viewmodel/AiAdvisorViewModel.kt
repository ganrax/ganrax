package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.network.gemini.ChatMessage
import com.example.network.gemini.GeminiSportsService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AiAdvisorViewModel(
    application: Application,
    private val geminiService: GeminiSportsService = GeminiSportsService(application)
) : AndroidViewModel(application) {

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                role = "model",
                content = "Szia! Én vagyok a TétMester Pro beépített AI fogadási és stratégiai asszisztense. Kérdezz bátran tétkalkulációról, a 4-körös stratégiáról, kármentésről vagy meccselemzésről!"
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasCustomApiKey = MutableStateFlow(geminiService.hasCustomApiKey())
    val hasCustomApiKey: StateFlow<Boolean> = _hasCustomApiKey.asStateFlow()

    fun sendMessage(userText: String) {
        if (userText.isBlank() || _isLoading.value) return

        val userMessage = ChatMessage(role = "user", content = userText)
        val currentList = _messages.value + userMessage
        _messages.value = currentList
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val reply = geminiService.sendChatMessage(
                    conversationHistory = currentList,
                    userMessage = userText
                )
                _messages.value = _messages.value + ChatMessage(role = "model", content = reply)
            } catch (e: Exception) {
                // Even on unhandled exception, invoke offline expert
                val fallback = geminiService.generateExpertOfflineResponse(userText, isFallbackFromNetwork = true)
                _messages.value = _messages.value + ChatMessage(role = "model", content = fallback)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setCustomApiKey(key: String) {
        geminiService.saveCustomApiKey(key)
        _hasCustomApiKey.value = geminiService.hasCustomApiKey()
    }

    fun clearCustomApiKey() {
        geminiService.clearCustomApiKey()
        _hasCustomApiKey.value = false
    }

    fun clearHistory() {
        _messages.value = listOf(
            ChatMessage(
                role = "model",
                content = "A beszélgetési előzmények törölve. Kérdezz bátran bármilyen fogadási stratégiáról, oddsról vagy meccselemzésről!"
            )
        )
    }

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AiAdvisorViewModel::class.java)) {
                return AiAdvisorViewModel(app) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
