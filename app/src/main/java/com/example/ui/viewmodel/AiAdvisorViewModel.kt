package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.network.gemini.ChatMessage
import com.example.network.gemini.GeminiSportsService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AiAdvisorViewModel(
    private val geminiService: GeminiSportsService = GeminiSportsService()
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                role = "model",
                content = "Szia! Én vagyok a TétMester Pro beépített AI fogadási és stratégiai asszisztense. Segítek kiszámolni a következő kör tétjét, optimalizálni a kármentést, értékelni a meccseket és megvédeni a bankrollodat. Miben segíthetek ma?"
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

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
                _messages.value = _messages.value + ChatMessage(
                    role = "model",
                    content = "Hiba történt a válasz generálása során: ${e.localizedMessage}"
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearHistory() {
        _messages.value = listOf(
            ChatMessage(
                role = "model",
                content = "A beszélgetési előzmények törölve. Kérdezz bátran bármilyen fogadási stratégiáról, oddsról vagy meccselemzésről!"
            )
        )
    }
}
