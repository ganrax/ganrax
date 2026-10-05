package com.example.network.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
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
        .followRedirects(true)
        .followSslRedirects(true)
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

        val repoPath = if (customUrl.isNotBlank()) customUrl.trim().removePrefix("https://github.com/").removePrefix("http://github.com/") else "dzsolt5/ganrax"
        val directFallbackApkUrl = "https://github.com/$repoPath/releases/latest/download/tetmester-pro-latest.apk"

        try {
            val targetApiUrl = "https://api.github.com/repos/$repoPath/releases/latest"

            val request = Request.Builder()
                .url(targetApiUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "TetMesterPro-Android")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonStr = response.body?.string() ?: ""
                val json = JSONObject(jsonStr)

                var downloadUrl = ""
                val releaseNotes = json.optString("body", "Új GitHub verzió elérhető!")
                val tagName = json.optString("tag_name", "v1.3.0")

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
                    downloadUrl = directFallbackApkUrl
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

            // If API didn't return 200, return direct fallback info
            val defaultInfo = AppUpdateInfo(
                currentVersionCode = currentCode,
                currentVersionName = currentName,
                latestVersionCode = currentCode + 1,
                latestVersionName = "v1.4.0 Pro",
                releaseNotes = """
                    • Egyetlen Letisztult Oldalon: Kalkulátor és Telegram Meccsek közvetlen kézi odds alapú számítással
                    • Felesleges fülek törölve: csak a Stratégia, Kalkulátor, Meccsek és Frissítő maradt
                    • Kézzel megadható oddsok és valós idejű tőkearányos tét/profit számítás
                    • Közvetlen Google Keresés és 1-kattintásos rögzítés a Meccsekhez
                    • Android 14 (API 34) natív optimalizáció és megbízható csomagtelepítés
                """.trimIndent(),
                downloadUrl = directFallbackApkUrl,
                isUpdateAvailable = true,
                fileSizeMb = 24.0,
                releaseDate = "2026-10-05"
            )

            _updateState.value = UpdateDownloadState.Available(defaultInfo)
            defaultInfo
        } catch (e: Exception) {
            val fallbackInfo = AppUpdateInfo(
                currentVersionCode = currentCode,
                currentVersionName = currentName,
                latestVersionCode = currentCode + 1,
                latestVersionName = "v1.3.0 Pro",
                releaseNotes = "GitHub Releases frissítés (dzsolt5/ganrax).",
                downloadUrl = directFallbackApkUrl,
                isUpdateAvailable = true
            )
            _updateState.value = UpdateDownloadState.Available(fallbackInfo)
            fallbackInfo
        }
    }

    suspend fun downloadAndPrepareApk(downloadUrl: String) = withContext(Dispatchers.IO) {
        try {
            _updateState.value = UpdateDownloadState.Downloading(0, 0, 100)

            // Prepare dedicated external/internal updates directory
            val updatesDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, "updates").apply { if (!exists()) mkdirs() }
            if (!updatesDir.exists()) updatesDir.mkdirs()

            val targetApk = File(updatesDir, "tetmester_pro_update.apk")
            if (targetApk.exists()) targetApk.delete()

            val actualUrl = if (downloadUrl.isNotBlank() && downloadUrl.startsWith("http")) {
                downloadUrl
            } else {
                "https://github.com/dzsolt5/ganrax/releases/latest/download/tetmester-pro-latest.apk"
            }

            val request = Request.Builder()
                .url(actualUrl)
                .header("User-Agent", "TetMesterPro-Android")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body

            if (!response.isSuccessful || body == null) {
                _updateState.value = UpdateDownloadState.Error("Nem sikerült letölteni az APK-t (HTTP ${response.code}). Ellenőrizd, hogy a GitHub Release elkészült-e a dzsolt5/ganrax oldalon!")
                return@withContext
            }

            val totalBytes = body.contentLength().let { if (it <= 0) 24_000_000L else it }
            var bytesRead = 0L
            val buffer = ByteArray(32 * 1024)

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

            // Validate that downloaded file is a genuine, non-corrupted APK archive
            if (!isValidApk(targetApk)) {
                targetApk.delete()
                _updateState.value = UpdateDownloadState.Error(
                    "A letöltött fájl érvénytelen vagy sérült (valószínűleg a GitHub még nem fejezte be a release csatolását). Kérlek ellenőrizd az Actions zöld pipáját!"
                )
                return@withContext
            }

            _updateState.value = UpdateDownloadState.ReadyToInstall(targetApk)
        } catch (e: Exception) {
            _updateState.value = UpdateDownloadState.Error("Hiba a letöltés során: ${e.localizedMessage}")
        }
    }

    private fun isValidApk(file: File): Boolean {
        if (!file.exists() || file.length() < 100_000) return false
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
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            // Grant explicit URI permissions to all matching package installer handlers
            val resInfoList = context.packageManager.queryIntentActivities(installIntent, PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(packageName, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
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
            pInfo.versionName ?: "1.3.0"
        } catch (e: Exception) {
            "1.3.0"
        }
    }
}
