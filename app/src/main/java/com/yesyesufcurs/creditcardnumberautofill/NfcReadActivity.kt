package com.yesyesufcurs.creditcardnumberautofill

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.yesyesufcurs.creditcardnumberautofill.nfc.CardData
import com.yesyesufcurs.creditcardnumberautofill.nfc.CardReadException
import com.yesyesufcurs.creditcardnumberautofill.nfc.EmvCardReader
import com.yesyesufcurs.creditcardnumberautofill.ui.theme.CreditCardNumberAutofillTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface ReadState {
    data object CheckingNfc : ReadState
    data object NoNfcHardware : ReadState
    data object NfcDisabled : ReadState
    data object Waiting : ReadState
    data class Error(val message: String) : ReadState
    data class Success(val card: CardData) : ReadState
}

class NfcReadActivity : ComponentActivity() {

    private val reader = EmvCardReader()
    private var reading = false
    private var notificationsGranted = true
    private var state by mutableStateOf<ReadState>(ReadState.CheckingNfc)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
        state = ReadState.Waiting
    }

    private val nfcCallback = NfcAdapter.ReaderCallback { tag ->
        if (reading || state is ReadState.Success) return@ReaderCallback
        reading = true
        lifecycleScope.launch {
            try {
                onCardRead(reader.read(tag))
            } catch (e: CardReadException) {
                state = ReadState.Error(e.message ?: getString(R.string.read_error))
            } finally {
                reading = false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationsGranted =
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!notificationsGranted) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        setContent {
            CreditCardNumberAutofillTheme {
                ReadScreen(
                    state = state,
                    onRetry = ::startReader,
                    onOpenNfcSettings = { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) },
                    onCopyNumber = { card ->
                        Clipboard.copy(this, "cardNumber", card.number)
                        toast(R.string.copied)
                    },
                    onCopyExpiry = { card ->
                        Clipboard.copy(this, "cardExpiry", card.expiryText.orEmpty())
                        toast(R.string.copied)
                    },
                    onCopyName = { card ->
                        Clipboard.copy(this, "cardName", card.holderName.orEmpty())
                        toast(R.string.copied)
                    },
                    onDone = ::onDone
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startReader()
    }

    override fun onPause() {
        super.onPause()
        NfcAdapter.getDefaultAdapter(this)?.disableReaderMode(this)
    }

    private fun startReader() {
        if (state is ReadState.Success) return
        val adapter = NfcAdapter.getDefaultAdapter(this)
        if (adapter == null) {
            state = ReadState.NoNfcHardware
            return
        }
        if (!adapter.isEnabled) {
            state = ReadState.NfcDisabled
            return
        }
        adapter.enableReaderMode(
            this,
            nfcCallback,
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )
        if (state !is ReadState.Waiting) state = ReadState.Waiting
    }

    private fun onCardRead(card: CardData) {
        CardCache.card = card
        state = ReadState.Success(card)
        vibrate()
        Clipboard.copy(this, "cardNumber", card.number)
        if (notificationsGranted) {
            CardNotifier.show(this, card)
            lifecycleScope.launch {
                delay(AUTO_FINISH_MS)
                finish()
            }
        }
    }

    private fun onDone() {
        Clipboard.clear(this)
        CardNotifier.dismiss(this)
        CardCache.card = null
        finish()
    }

    private fun vibrate() {
        val vibrator = getSystemService(VibratorManager::class.java).defaultVibrator
        vibrator.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun toast(resId: Int) {
        Toast.makeText(this, getString(resId), Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val AUTO_FINISH_MS = 4_000L
    }
}

@Composable
private fun ReadScreen(
    state: ReadState,
    onRetry: () -> Unit,
    onOpenNfcSettings: () -> Unit,
    onCopyNumber: (CardData) -> Unit,
    onCopyExpiry: (CardData) -> Unit,
    onCopyName: (CardData) -> Unit,
    onDone: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(32.dp))

            when (state) {
                is ReadState.CheckingNfc -> CircularProgressIndicator()
                is ReadState.NoNfcHardware -> StatusMessage(stringResource(R.string.read_error_no_nfc))
                is ReadState.NfcDisabled -> {
                    StatusMessage(stringResource(R.string.read_error_nfc_disabled))
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onOpenNfcSettings) {
                        Text(stringResource(R.string.action_enable_nfc))
                    }
                }

                is ReadState.Waiting -> {
                    androidx.compose.material3.Icon(
                        painter = painterResource(R.drawable.ic_qs_tile),
                        contentDescription = null,
                        modifier = Modifier.size(96.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = stringResource(R.string.read_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.read_hint),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(24.dp))
                    CircularProgressIndicator()
                }

                is ReadState.Error -> {
                    StatusMessage(state.message)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry) {
                        Text(stringResource(R.string.read_retry))
                    }
                }

                is ReadState.Success -> SuccessContent(
                    card = state.card,
                    onCopyNumber = onCopyNumber,
                    onCopyExpiry = onCopyExpiry,
                    onCopyName = onCopyName,
                    onDone = onDone
                )
            }
        }
    }
}

@Composable
private fun SuccessContent(
    card: CardData,
    onCopyNumber: (CardData) -> Unit,
    onCopyExpiry: (CardData) -> Unit,
    onCopyName: (CardData) -> Unit,
    onDone: () -> Unit
) {
    Text(
        text = stringResource(R.string.read_success),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(16.dp))

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(card.network.displayName, fontWeight = FontWeight.Bold)
            Text(card.maskedNumber, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            card.expiryText?.let {
                Spacer(Modifier.height(4.dp))
                Text("${stringResource(R.string.expiry_format)}: $it")
            }
            card.holderName?.let {
                Spacer(Modifier.height(4.dp))
                Text(it)
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.read_copied),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.read_steps_hint),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(Modifier.height(24.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(onClick = { onCopyNumber(card) }, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.copy_number), fontSize = 12.sp)
        }
        OutlinedButton(onClick = { onCopyExpiry(card) }, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.copy_expiry), fontSize = 12.sp)
        }
    }
    if (card.holderName != null) {
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { onCopyName(card) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.copy_name))
        }
    }
    Spacer(Modifier.height(8.dp))
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.done))
    }
}

@Composable
private fun StatusMessage(text: String) {
    Text(
        text = text,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyLarge
    )
}
