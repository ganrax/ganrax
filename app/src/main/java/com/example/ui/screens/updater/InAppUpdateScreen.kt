package com.example.ui.screens.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.network.updater.UpdateDownloadState
import com.example.ui.components.AppHeader
import com.example.ui.theme.*
import com.example.ui.viewmodel.UpdateViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InAppUpdateScreen(
    viewModel: UpdateViewModel
) {
    val context = LocalContext.current
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val customUrl by viewModel.customUrlInput.collectAsStateWithLifecycle()
    val token by viewModel.tokenInput.collectAsStateWithLifecycle()
    val lastUpdateInfo by viewModel.lastUpdateInfo.collectAsStateWithLifecycle()

    var showCustomUrlDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (lastUpdateInfo == null) {
            viewModel.checkForUpdates()
        }
    }

    Scaffold(
        topBar = {
            AppHeader(
                title = "Alkalmazás Frissítő",
                subtitle = "Alkalmazáson belüli önfrissítés (USB nélkül)"
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Version Info Banner Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldPrimary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SystemUpdate,
                                        contentDescription = null,
                                        tint = EmeraldPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = "TétMester Pro",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Telepített verzió: v${lastUpdateInfo?.currentVersionName ?: "1.0.0"} (Build ${lastUpdateInfo?.currentVersionCode ?: 1})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (lastUpdateInfo?.isUpdateAvailable == true) GoldOdds.copy(alpha = 0.2f) else StatusWon.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (lastUpdateInfo?.isUpdateAvailable == true) GoldOdds else StatusWon
                                )
                            ) {
                                Text(
                                    text = if (lastUpdateInfo?.isUpdateAvailable == true) "Frissítés elérhető" else "Naprakész",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (lastUpdateInfo?.isUpdateAvailable == true) GoldOdds else StatusWon
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Check Updates Button
                        Button(
                            onClick = { viewModel.checkForUpdates() },
                            modifier = Modifier.fillMaxWidth().testTag("check_updates_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Frissítések Keresése Most", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // GitHub Private Access Token Card (2. Lehetőség - Privát repó appon belüli közvetlen elérése)
            item {
                val clipboardManager = LocalClipboardManager.current
                var tokenText by remember(token) { mutableStateOf(token) }
                var tokenVisible by remember { mutableStateOf(false) }

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (token.isNotBlank()) EmeraldPrimary.copy(alpha = 0.08f) else GoldOdds.copy(alpha = 0.08f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (token.isNotBlank()) EmeraldPrimary.copy(alpha = 0.4f) else GoldOdds.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VpnKey,
                                    contentDescription = null,
                                    tint = if (token.isNotBlank()) EmeraldPrimary else GoldOdds,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "Privát GitHub Kulcs (Token)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (token.isNotBlank()) StatusWon else GoldOdds
                            ) {
                                Text(
                                    text = if (token.isNotBlank()) "AKTÍV ✓" else "SZÜKSÉGES",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = if (token.isNotBlank()) {
                                "✓ A privát GitHub kulcsod el van mentve. A letöltés gombra kattintva az app közvetlenül, jelszókérés nélkül frissíti önmagát a zárt ganrax/ganrax repódból!"
                            } else {
                                "A kódod 100%-ban privát és védett. Ahhoz, hogy az app közvetlenül innen, a zárt repóból töltse le a frissítést, illeszd be a GitHub Tokenedet (csak egyszer kell megadni)!"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = tokenText,
                            onValueChange = { tokenText = it },
                            placeholder = { Text("ghp_... (GitHub Personal Access Token)") },
                            singleLine = true,
                            visualTransformation = if (tokenVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { tokenVisible = !tokenVisible }) {
                                    Icon(
                                        imageVector = if (tokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Action Buttons: Paste from Clipboard, Save, Clear
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val clip = clipboardManager.getText()?.text
                                    if (!clip.isNullOrBlank()) {
                                        tokenText = clip.trim()
                                        viewModel.setToken(clip.trim())
                                        Toast.makeText(context, "Token beillesztve és elmentve!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "A vágólap üres!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Beillesztés", fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    if (tokenText.isNotBlank()) {
                                        viewModel.setToken(tokenText.trim())
                                        Toast.makeText(context, "Token sikeresen elmentve!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.setToken("")
                                        Toast.makeText(context, "Token törölve!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Save, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Mentés", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                        }

                        // Generate Token quick link
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://github.com/settings/tokens/new?scopes=repo&description=TetMesterProUpdater")
                                    )
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Hiba: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp), tint = GoldOdds)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Token Generálása a GitHubon (1 kattintás)", fontSize = 12.sp, color = GoldOdds, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Download & Install Action Section
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Közvetlen Telepítés (Alkalmazáson belül)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "A frissítés automatikusan letöltődik és közvetlenül a készüléken települ. Nem szükséges USB kábellel számítógéphez csatlakoztatni a telefont!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        when (val state = updateState) {
                            is UpdateDownloadState.Idle, is UpdateDownloadState.Available -> {
                                Button(
                                    onClick = {
                                        if (token.isBlank()) {
                                            Toast.makeText(
                                                context,
                                                "Kérlek illeszd be a fenti 'Privát GitHub Kulcs' mezőbe a tokenedet a letöltéshez!",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            val url = lastUpdateInfo?.downloadUrl ?: ""
                                            val assetUrl = lastUpdateInfo?.assetApiUrl ?: ""
                                            viewModel.startDownload(url, assetUrl)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldPrimary,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("download_update_button")
                                ) {
                                    Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Új Verzió Letöltése és Telepítése (${lastUpdateInfo?.latestVersionName ?: "v1.5.0"})",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            is UpdateDownloadState.Checking -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = CyanAccent)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Frissítési szerver lekérdezése...", style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            is UpdateDownloadState.Downloading -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "APK Letöltése folyamatban...",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "${state.progressPercent}%",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = EmeraldPrimary
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { state.progressPercent / 100f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = EmeraldPrimary
                                    )
                                }
                            }

                            is UpdateDownloadState.ReadyToInstall -> {
                                Button(
                                    onClick = {
                                        val success = viewModel.installApk(state.apkFile)
                                        if (!success) {
                                            Toast.makeText(context, "Engedélyezd az ismeretlen forrásból származó telepítést!", Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldPrimary,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("install_apk_button")
                                ) {
                                    Icon(imageVector = Icons.Default.InstallMobile, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Telepítés Indítása Most", fontWeight = FontWeight.Bold)
                                }
                            }

                            is UpdateDownloadState.UpToDate -> {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = StatusWon.copy(alpha = 0.15f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = StatusWon)
                                        Text("Az alkalmazás a legfrissebb verziót futtatja!", fontWeight = FontWeight.SemiBold, color = StatusWon)
                                    }
                                }
                            }

                            is UpdateDownloadState.Error -> {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = StatusLost.copy(alpha = 0.15f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(text = state.message, color = StatusLost, style = MaterialTheme.typography.bodySmall)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            TextButton(onClick = { viewModel.reset() }) {
                                                Text("Újrapróbálás", color = EmeraldPrimary)
                                            }
                                            TextButton(onClick = {
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ganrax/ganrax/releases/latest/download/tetmester-pro-latest.apk"))
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Nem sikerült megnyitni a böngészőt", Toast.LENGTH_SHORT).show()
                                                }
                                            }) {
                                                Text("Böngészős Letöltés", color = GoldOdds)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Direct Browser Download Alternative for Android 14
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ganrax/ganrax/releases"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Hiba: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = GoldOdds, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Megnyitás a GitHub Appban (100% Privát)", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ganrax/ganrax/releases/latest/download/tetmester-pro-latest.apk"))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Hiba: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Böngészős Letöltés", fontSize = 12.sp)
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
                                            context.startActivity(intent)
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Telepítés Engedélyezése", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Release Notes & Changelog Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.History, contentDescription = null, tint = EmeraldPrimary)
                            Text(
                                text = "Verziókövető & Kiadási Napló (Changelog)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                        // v1.5.0
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = EmeraldPrimary.copy(alpha = 0.08f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("v1.5.0 (Legújabb verzió)", fontWeight = FontWeight.Bold, color = EmeraldPrimary, style = MaterialTheme.typography.titleSmall)
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = EmeraldPrimary,
                                        modifier = Modifier.padding(2.dp)
                                    ) {
                                        Text("AKTÍV", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                                Text("• Verziószám automatikus léptetése (v1.5.0) és külön GitHub Release bejegyzés generálása", style = MaterialTheme.typography.bodySmall)
                                Text("• Közvetlen Telegram meccskinyerés és kézi odds alapú tétkezelés egyetlen képernyőn", style = MaterialTheme.typography.bodySmall)
                                Text("• Közvetlen Google kereső link minden beillesztett meccshez", style = MaterialTheme.typography.bodySmall)
                                Text("• Android 14 (API 34) optimalizáció és állandó aláírókulcs", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        // v1.4.0
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("v1.4.0 (Előző kiadás)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall)
                                Text("• Kalkulátor és Telegram kézi odds alapú tétkezelés integrációja", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        // v1.3.0
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("v1.3.0 (Előző kiadás)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall)
                                Text("• Telegram Értesítés Feldolgozó és Google kereső linkek generálása", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        // v1.2.0
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("v1.2.0 (Előző kiadás)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall)
                                Text("• Automatikus tőkearányos tétnövekedés (Tőke / 49.25 az Excel tábla szerint)", style = MaterialTheme.typography.bodySmall)
                                Text("• Zárolt meccs tétkalkuláció: odds és kör alapján számol", style = MaterialTheme.typography.bodySmall)
                                Text("• Állandó aláírókulcs és beépített verziókövető", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        // v1.1.0
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("v1.1.0 (Előző kiadás)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall)
                                Text("• 100 Napos Kamatos Kamat Terv és napi interaktív napváltó", style = MaterialTheme.typography.bodySmall)
                                Text("• Élő mérkőzéskövető, eredményrögzítő és automatikus bankroll jóváírás", style = MaterialTheme.typography.bodySmall)
                                Text("• Gemini AI meccselemző és fogadási stratéga asszisztens", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        // v1.0.0
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("v1.0.0 (Kezdeti kiadás)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall)
                                Text("• 4-Körös Martingale és Kármentés tétkalkulátor Excel minták alapján", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // GitHub Releases & Custom APK Source
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CloudSync, contentDescription = null, tint = CyanAccent)
                            Text(
                                text = "GitHub Repository & Frissítési Forrás",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = EmeraldPrimary.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "100% Privát Repózitórium: a kódod és a programod titkosított, senki más nem fér hozzá!",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Text(
                            text = "GitHub repository (ganrax/ganrax):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = customUrl,
                            onValueChange = { viewModel.setCustomUrl(it) },
                            placeholder = { Text("ganrax/ganrax") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { viewModel.checkForUpdates() }) {
                                    Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Lekérdezés")
                                }
                            }
                        )

                        Text(
                            text = "GitHub Token (Privát repóhoz - Opcionális):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = token,
                            onValueChange = { viewModel.setToken(it) },
                            placeholder = { Text("ghp_... (ha az appon belülről töltenéd le)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // GitHub & Installation Guide Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "ℹ️ Útmutató: Hogyan működik a GitHub frissítés?",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "1. AI Studio jobb felső menüjében válaszd a 'Push to GitHub' opciót a kód feltöltéséhez.\n2. A beállított GitHub Actions (.github/workflows/release-apk.yml) automatikusan lefordítja a kész APK-t a GitHubon.\n3. Az alkalmazás a GitHub Releases API-n keresztül közvetlenül letölti az érvényes APK-t és megnyitja a telepítőt.\n4. A 'gond volt a csomag telepítésével' hiba azért fordult elő korábban, mert valós APK nélkül a telefon nem tudta értelmezni a tesztfájlt. Mostantól a rendszer ellenőrzi a csomag sértetlenségét.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }
}
