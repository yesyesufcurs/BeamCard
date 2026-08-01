package com.yesyesufcurs.creditcardnumberautofill

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.quicksettings.TileService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yesyesufcurs.creditcardnumberautofill.ui.theme.BeamCardTheme

class MainActivity : ComponentActivity() {

    private var refresh by mutableStateOf(0)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            refresh++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BeamCardTheme {
                MainScreen(
                    refresh = refresh,
                    onReadCard = { startActivity(Intent(this, NfcReadActivity::class.java)) },
                    onOpenNfcSettings = { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) },
                    onRequestNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onAddTile = ::addTile
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh++
    }

    private fun addTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            TileService.requestListeningState(
                this,
                ComponentName(this, CardAutofillTileService::class.java)
            )
        }
    }
}

@Composable
private fun MainScreen(
    refresh: Int,
    onReadCard: () -> Unit,
    onOpenNfcSettings: () -> Unit,
    onRequestNotifications: () -> Unit,
    onAddTile: () -> Unit
) {
    val context = LocalContext.current
    val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
    val hasNfc = nfcAdapter != null
    val nfcEnabled = nfcAdapter?.isEnabled == true
    val notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    var showTileHelp by remember { mutableStateOf(false) }

    if (showTileHelp) {
        AlertDialog(
            onDismissRequest = { showTileHelp = false },
            title = { Text(stringResource(R.string.tile_help_title)) },
            text = { Text(stringResource(R.string.tile_help_text)) },
            confirmButton = {
                TextButton(onClick = { showTileHelp = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.main_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            StatusRow(stringResource(R.string.status_nfc_hardware), hasNfc)
            StatusRow(
                label = stringResource(R.string.status_nfc_enabled),
                on = nfcEnabled,
                actionLabel = if (!nfcEnabled) stringResource(R.string.action_enable_nfc) else null,
                onAction = onOpenNfcSettings
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                StatusRow(
                    label = stringResource(R.string.status_notifications),
                    on = notificationsGranted,
                    actionLabel = if (!notificationsGranted) {
                        stringResource(R.string.action_request_notifications)
                    } else {
                        null
                    },
                    onAction = onRequestNotifications
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(onClick = onReadCard, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_read_card))
            }
            OutlinedButton(
                onClick = {
                    onAddTile()
                    showTileHelp = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.action_setup_tile))
            }
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    on: Boolean,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(if (on) R.string.status_on else R.string.status_off),
                style = MaterialTheme.typography.bodyMedium,
                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}
