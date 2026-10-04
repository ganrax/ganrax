package com.example.network.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.domain.model.AppUpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()
    object Checking : UpdateDownloadState()
    data class Available(val updateInfo: AppUpdateInfo) : UpdateDownloadState()
    object UpToDate : UpdateDownloadState()
    data class Downloading(val progressPercent: Int, val bytesDownloaded: Long, val totalBytes: Long) : UpdateDownloadState()
    data class ReadyToInstall(val apkFile: File) : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}

class InAppUpdaterService(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _updateState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val updateState: StateFlow<UpdateDownloadState> = _updateState.asStateFlow()

    fun resetState() {
        _updateState.value = UpdateDownloadState.Idle
    }

    suspend fun checkForUpdates(customUrl: String = ""): AppUpdateInfo = withContext(Dispatchers.IO) {
        _updateState.value = UpdateDownloadState.Checking
        val currentCode = getCurrentVersionCode()
        val currentName = getCurrentVersionName()

        try {
            // Check if user provided a GitHub repo or direct releases API / JSON URL
            val targetUrl = when {
                customUrl.isNotBlank() && customUrl.startsWith("http") -> customUrl
                customUrl.isNotBlank() && customUrl.contains("/") -> "https://api.github.com/repos/${customUrl.trim().removePrefix("https://github.com/")}/releases/latest"
                else -> ""
            }

            if (targetUrl.isNotBlank()) {
                val request = Request.Builder()
                    .url(targetUrl)
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string() ?: ""
                    val json = JSONObject(jsonStr)

                    var downloadUrl = ""
                    var releaseNotes = json.optString("body", "Új GitHub verzió elérhető!")
                    val tagName = json.optString("tag_name", "v1.1.0")

                    // Check GitHub Release assets for .apk file
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                downloadUrl = asset.optString("browser_download_url", "")
                                break
                            }
                        }
                    }

                    if (downloadUrl.isBlank()) {
                        downloadUrl = json.optString("downloadUrl", "")
                    }

                    val info = AppUpdateInfo(
                        currentVersionCode = currentCode,
                        currentVersionName = currentName,
                        latestVersionCode = currentCode + 1,
                        latestVersionName = tagName,
                        releaseNotes = releaseNotes,
                        downloadUrl = downloadUrl,
                        isUpdateAvailable = true
                    )
                    _updateState.value = UpdateDownloadState.Available(info)
                    return@withContext info
                }
            }

            // Default Release Info for TétMester Pro
            val defaultInfo = AppUpdateInfo(
                currentVersionCode = currentCode,
                currentVersionName = currentName,
                latestVersionCode = currentCode + 1,
                latestVersionName = "v1.2.0 Pro",
                releaseNotes = """
                    • 100 Napos Kamatos Kamat Tétkezelő 2.0 (Valós idejű bankroll szimuláció)
                    • Automatikus tőkearányos tétnövekedés (Tőke / 49.25)
                    • 4-Körös és Kármentés Tétkalkulátor közvetlen meccs-hozzárendeléssel
                    • Élő mérkőzéskövető, eredményrögzítő és statisztikai ROI számítás
                    • Gemini AI meccselemző és fogadási stratéga asszisztens
                    • Közvetlen GitHub Releases APK frissítés
                """.trimIndent(),
                downloadUrl = "",
                isUpdateAvailable = true,
                fileSizeMb = 24.0,
                releaseDate = "2026-10-04"
            )

            _updateState.value = UpdateDownloadState.Available(defaultInfo)
            defaultInfo
        } catch (e: Exception) {
            _updateState.value = UpdateDownloadState.Error("Nem sikerült elérni a frissítési kiszolgálót: ${e.localizedMessage}")
            AppUpdateInfo(
                currentVersionCode = currentCode,
                currentVersionName = currentName,
                isUpdateAvailable = false
            )
        }
    }

    suspend fun downloadAndPrepareApk(downloadUrl: String) = withContext(Dispatchers.IO) {
        try {
            _updateState.value = UpdateDownloadState.Downloading(0, 0, 100)
            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists()) updatesDir.mkdirs()
            val targetApk = File(updatesDir, "tetmester_update.apk")

            if (downloadUrl.startsWith("http")) {
                val request = Request.Builder().url(downloadUrl).build()
                val response = client.newCall(request).execute()
                val body = response.body

                if (!response.isSuccessful || body == null) {
                    _updateState.value = UpdateDownloadState.Error("Nem sikerült letölteni az APK fájlt a megadott címről (HTTP ${response.code}). Kérlek ellenőrizd a GitHub Release linket!")
                    return@withContext
                }

                val totalBytes = body.contentLength().let { if (it <= 0) 24_000_000L else it }
                var bytesRead = 0L
                val buffer = ByteArray(16 * 1024)

                body.byteStream().use { input ->
                    FileOutputStream(targetApk).use { output ->
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesRead += read
                            val percent = ((bytesRead.toDouble() / totalBytes) * 100).toInt().coerceIn(0, 100)
                            _updateState.value = UpdateDownloadState.Downloading(percent, bytesRead, totalBytes)
                        }
                    }
                }
            } else {
                // Check if we have an internal asset package
                try {
                    val assetManager = context.assets
                    val hasAsset = try {
                        assetManager.open("latest-release.apk").close()
                        true
                    } catch (e: Exception) {
                        false
                    }

                    if (hasAsset) {
                        assetManager.open("latest-release.apk").use { input ->
                            FileOutputStream(targetApk).use { output ->
                                input.copyTo(output)
                            }
                        }
                        _updateState.value = UpdateDownloadState.Downloading(100, targetApk.length(), targetApk.length())
                    } else {
                        _updateState.value = UpdateDownloadState.Error("Nincs megadva érvényes letöltési link. Kérlek másold be a GitHub Release APK linkjét a frissítéshez!")
                        return@withContext
                    }
                } catch (e: Exception) {
                    _updateState.value = UpdateDownloadState.Error("Hiba az APK előkészítése során: ${e.localizedMessage}")
                    return@withContext
                }
            }

            // CRITICAL: Validate that targetApk is a genuine valid APK
            if (!isValidApk(targetApk)) {
                _updateState.value = UpdateDownloadState.Error(
                    "A letöltött fájl nem érvényes Android APK csomag (valószínűleg hibás link vagy még nincs feltöltve az APK a GitHub kiadáshoz). Ezért a rendszer elutasította a telepítést."
                )
                return@withContext
            }

            _updateState.value = UpdateDownloadState.ReadyToInstall(targetApk)
        } catch (e: Exception) {
            _updateState.value = UpdateDownloadState.Error("Hiba a letöltés során: ${e.localizedMessage}")
        }
    }

    private fun isValidApk(file: File): Boolean {
        if (!file.exists() || file.length() < 10000) return false
        return try {
            ZipFile(file).use { zip ->
                zip.getEntry("AndroidManifest.xml") != null
            }
        } catch (e: Exception) {
            false
        }
    }

    fun triggerPackageInstall(apkFile: File): Boolean {
        try {
            if (!isValidApk(apkFile)) {
                _updateState.value = UpdateDownloadState.Error("A fájl nem érvényes APK csomag.")
                return false
            }

            // Check unknown sources permission on Android 8.0+ (API 26+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return false
                }
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
            return true
        } catch (e: Exception) {
            _updateState.value = UpdateDownloadState.Error("Telepítés indítása sikertelen: ${e.localizedMessage}")
            return false
        }
    }

    private fun getCurrentVersionCode(): Int {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (e: Exception) {
            1
        }
    }

    private fun getCurrentVersionName(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }
}
