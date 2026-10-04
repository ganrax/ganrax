package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.model.AppUpdateInfo
import com.example.network.updater.InAppUpdaterService
import com.example.network.updater.UpdateDownloadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class UpdateViewModel(private val updaterService: InAppUpdaterService) : ViewModel() {

    val updateState: StateFlow<UpdateDownloadState> = updaterService.updateState

    private val _customUrlInput = MutableStateFlow("dzsolt5/ganrax")
    val customUrlInput: StateFlow<String> = _customUrlInput.asStateFlow()

    private val _lastUpdateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val lastUpdateInfo: StateFlow<AppUpdateInfo?> = _lastUpdateInfo.asStateFlow()

    init {
        checkForUpdates()
    }

    fun setCustomUrl(url: String) {
        _customUrlInput.value = url
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            val info = updaterService.checkForUpdates(_customUrlInput.value)
            _lastUpdateInfo.value = info
        }
    }

    fun startDownload(downloadUrl: String) {
        viewModelScope.launch {
            updaterService.downloadAndPrepareApk(downloadUrl)
        }
    }

    fun installApk(apkFile: File): Boolean {
        return updaterService.triggerPackageInstall(apkFile)
    }

    fun reset() {
        updaterService.resetState()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return UpdateViewModel(InAppUpdaterService(context.applicationContext)) as T
        }
    }
}
