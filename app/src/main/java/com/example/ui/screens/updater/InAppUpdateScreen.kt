package com.example.ui.screens.updater

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
                                        val url = lastUpdateInfo?.downloadUrl ?: "https://example.com/app.apk"
                                        viewModel.startDownload(url)
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
                                        text = "Új Verzió Letöltése és Telepítése (${lastUpdateInfo?.latestVersionName ?: "v1.2.0"})",
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
                                        TextButton(onClick = { viewModel.reset() }) {
                                            Text("Újrapróbálás")
                                        }
                                    }
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
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Legújabb Változások és Újdonságok",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        Text(
                            text = lastUpdateInfo?.releaseNotes ?: "Rendszeroptimalizációk és hibajavítások.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
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

                        Text(
                            text = "Írd be a GitHub repository-d nevét (pl. dzsolt5/tetmester-pro) vagy a közvetlen Release APK linket az automatikus GitHub frissítéshez:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = customUrl,
                            onValueChange = { viewModel.setCustomUrl(it) },
                            placeholder = { Text("pl. felhasznalonev/repo vagy közvetlen URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { viewModel.checkForUpdates() }) {
                                    Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Lekérdezés")
                                }
                            }
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
