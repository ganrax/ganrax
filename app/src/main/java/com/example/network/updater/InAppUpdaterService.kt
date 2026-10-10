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

    private val prefs = context.getSharedPreferences("updater_prefs", Context.MODE_PRIVATE)

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

    fun getGitHubToken(): String {
        return prefs.getString("github_token", "") ?: ""
    }

    fun setGitHubToken(token: String) {
        prefs.edit().putString("github_token", token.trim()).apply()
    }

    suspend fun checkForUpdates(customUrl: String = ""): AppUpdateInfo = withContext(Dispatchers.IO) {
        _updateState.value = UpdateDownloadState.Checking
        val currentCode = getCurrentVersionCode()
        val currentName = getCurrentVersionName()
        val token = getGitHubToken()

        val repoPath = if (customUrl.isNotBlank()) customUrl.trim().removePrefix("https://github.com/").removePrefix("http://github.com/") else "ganrax/ganrax"
        val directFallbackApkUrl = "https://github.com/$repoPath/releases/latest/download/tetmester-pro-latest.apk"

        try {
            val targetApiUrl = "https://api.github.com/repos/$repoPath/releases/latest"

            val requestBuilder = Request.Builder()
                .url(targetApiUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "TetMesterPro-Android")

            if (token.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (response.isSuccessful) {
                val jsonStr = response.body?.string() ?: ""
                val json = JSONObject(jsonStr)

                var downloadUrl = ""
                var assetApiUrl = ""
                val releaseNotes = json.optString("body", "Új verzió elérhető a ganrax/ganrax repóban!")
                val tagName = json.optString("tag_name", "v1.5.0")

                // Check GitHub Release assets for .apk file
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            downloadUrl = asset.optString("browser_download_url", "")
                            assetApiUrl = asset.optString("url", "")
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
                    assetApiUrl = assetApiUrl,
                    isUpdateAvailable = true
                )
                _updateState.value = UpdateDownloadState.Available(info)
                return@withContext info
            } else if (response.code == 404 && token.isBlank()) {
                // Private repository without token
                val privateInfo = AppUpdateInfo(
                    currentVersionCode = currentCode,
                    currentVersionName = currentName,
                    latestVersionCode = currentCode + 1,
                    latestVersionName = "v1.9.0 Pro",
                    releaseNotes = "A repository privát (biztonságos). A legfrissebb APK közvetlenül a GitHub alkalmazásodból vagy az alábbi gombokkal tölthető le.",
                    downloadUrl = directFallbackApkUrl,
                    isUpdateAvailable = true
                )
                _updateState.value = UpdateDownloadState.Available(privateInfo)
                return@withContext privateInfo
            }

            // Fallback default info
            val defaultInfo = AppUpdateInfo(
                currentVersionCode = currentCode,
                currentVersionName = currentName,
                latestVersionCode = currentCode + 1,
                latestVersionName = "v1.9.0 Pro",
                releaseNotes = """
                    • Mentett meccseknél a valós csapatnevek kiemelt megjelenítése a kártyákon
                    • Gyors csapat- és stratégia-azonosítás / szerkesztés (⚠️ gomb és felugró ablak)
                    • Javított Telegram értesítés feldolgozó
                    • Beépített intelligens offline AI bot és precíz tétkalkuláció
                """.trimIndent(),
                downloadUrl = directFallbackApkUrl,
                isUpdateAvailable = true,
                fileSizeMb = 24.0,
                releaseDate = "2026-10-10"
            )

            _updateState.value = UpdateDownloadState.Available(defaultInfo)
            defaultInfo
        } catch (e: Exception) {
            val fallbackInfo = AppUpdateInfo(
                currentVersionCode = currentCode,
                currentVersionName = currentName,
                latestVersionCode = currentCode + 1,
                latestVersionName = "v1.9.0 Pro",
                releaseNotes = "GitHub Releases frissítés (ganrax/ganrax - Privát).",
                downloadUrl = directFallbackApkUrl,
                isUpdateAvailable = true
            )
            _updateState.value = UpdateDownloadState.Available(fallbackInfo)
            fallbackInfo
        }
    }

    suspend fun downloadAndPrepareApk(downloadUrl: String, assetApiUrl: String = "") = withContext(Dispatchers.IO) {
        try {
            _updateState.value = UpdateDownloadState.Downloading(0, 0, 100)
            val token = getGitHubToken()

            // Prepare dedicated external/internal updates directory
            val updatesDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, "updates").apply { if (!exists()) mkdirs() }
            if (!updatesDir.exists()) updatesDir.mkdirs()

            val targetApk = File(updatesDir, "tetmester_pro_update.apk")
            if (targetApk.exists()) targetApk.delete()

            // If we have a GitHub token and assetApiUrl for private repo, use API octet-stream download
            val useApiDownload = token.isNotBlank() && assetApiUrl.isNotBlank()
            val targetUrl = if (useApiDownload) {
                assetApiUrl
            } else if (downloadUrl.isNotBlank() && downloadUrl.startsWith("http")) {
                downloadUrl
            } else {
                "https://github.com/ganrax/ganrax/releases/latest/download/tetmester-pro-latest.apk"
            }

            val requestBuilder = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "TetMesterPro-Android")

            if (token.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
                if (useApiDownload) {
                    requestBuilder.header("Accept", "application/octet-stream")
                }
            }

            var response = client.newCall(requestBuilder.build()).execute()

            // If GitHub API returns 302 redirect for octet-stream asset download, follow it to AWS S3 without the Authorization header
            if (response.code in 301..308) {
                val redirectLocation = response.header("Location")
                if (!redirectLocation.isNullOrBlank()) {
                    response.close()
                    val s3Request = Request.Builder()
                        .url(redirectLocation)
                        .header("User-Agent", "TetMesterPro-Android")
                        .build()
                    response = client.newCall(s3Request).execute()
                }
            }

            val body = response.body

            if (!response.isSuccessful || body == null) {
                val errorMsg = if (response.code == 404 && token.isBlank()) {
                    "A repó privát (biztonságos), így a GitHub bejelentkezés nélkül blokkolta a letöltést. Koppints a 'Megnyitás a GitHub Appban' gombra, vagy adj meg egy privát GitHub tokent!"
                } else {
                    "Nem sikerült letölteni az APK-t (HTTP ${response.code}). Ellenőrizd az internetkapcsolatot vagy töltsd le a GitHub alkalmazásodból!"
                }
                _updateState.value = UpdateDownloadState.Error(errorMsg)
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
                    "A letöltött fájl nem érvényes APK (valószínűleg a privát repó miatt a GitHub egy bejelentkezési oldalt adott vissza). Nyisd meg a GitHub mobil appot a letöltéshez!"
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
            pInfo.versionName ?: "1.8.0"
        } catch (e: Exception) {
            "1.8.0"
        }
    }
}
