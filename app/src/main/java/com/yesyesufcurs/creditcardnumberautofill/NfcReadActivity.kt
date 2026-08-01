package com.yesyesufcurs.creditcardnumberautofill

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
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
import com.github.devnied.emvnfccard.exception.CommunicationException
import com.github.devnied.emvnfccard.iso7816emv.EmvTags
import com.github.devnied.emvnfccard.model.EmvCard
import com.github.devnied.emvnfccard.model.EmvTrack2
import com.github.devnied.emvnfccard.parser.EmvTemplate
import com.github.devnied.emvnfccard.parser.IProvider
import com.github.devnied.emvnfccard.utils.TlvUtil
import com.yesyesufcurs.creditcardnumberautofill.nfc.CardData
import com.yesyesufcurs.creditcardnumberautofill.ui.theme.CreditCardNumberAutofillTheme
import fr.devnied.bitlib.BytesUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Calendar

private sealed interface ReadState {
    data object CheckingNfc : ReadState
    data object NoNfcHardware : ReadState
    data object NfcDisabled : ReadState
    data object Waiting : ReadState
    data class Error(val message: String) : ReadState
    data class Success(val card: CardData) : ReadState
}

class NfcReadActivity : ComponentActivity() {

    private var notificationsGranted = true
    private var state by mutableStateOf<ReadState>(ReadState.CheckingNfc)
    private var reading = false

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
                val cardData = readCard(tag)
                if (cardData != null) {
                    onCardRead(cardData)
                } else {
                    state = ReadState.Error(getString(R.string.read_error))
                }
            } catch (e: Exception) {
                state = ReadState.Error(e.message ?: getString(R.string.read_error))
            } finally {
                reading = false
            }
        }
    }

    private suspend fun readCard(tag: Tag): CardData? = withContext(Dispatchers.IO) {
        val isoDep = IsoDep.get(tag) ?: return@withContext null
        try {
            isoDep.timeout = 5000
            isoDep.connect()
            val provider = NfcProvider(isoDep)
            val config = EmvTemplate.Config().apply {
                readTransactions = false
                readAllAids = true
                contactLess = true
                readAt = true
            }
            val parser = EmvTemplate.Builder()
                .setProvider(provider)
                .setConfig(config)
                .build()
            
            // Add custom parser to capture Tag 5A (PAN) and 5F24 (Expiry) if tracks are missing
            parser.addParsers(object : com.github.devnied.emvnfccard.parser.impl.EmvParser(parser) {
                override fun extractTrackData(pEmvCard: EmvCard, pData: ByteArray): Boolean {
                    super.extractTrackData(pEmvCard, pData)
                    
                    if (pEmvCard.cardNumber == null) {
                        val pan = TlvUtil.getValue(pData, EmvTags.PAN)
                        if (pan != null) {
                            val panStr = BytesUtils.bytesToStringNoSpace(pan).trimEnd('F')
                            if (pEmvCard.track2 == null) pEmvCard.track2 = EmvTrack2()
                            pEmvCard.track2.cardNumber = panStr
                        }
                    }
                    
                    if (pEmvCard.expireDate == null) {
                        val date = TlvUtil.getValue(pData, EmvTags.APP_EXPIRATION_DATE)
                        if (date != null) {
                            val dateStr = BytesUtils.bytesToStringNoSpace(date)
                            if (dateStr.length >= 4) {
                                try {
                                    val year = 2000 + dateStr.substring(0, 2).toInt()
                                    val month = dateStr.substring(2, 4).toInt()
                                    val cal = Calendar.getInstance().apply {
                                        set(Calendar.YEAR, year)
                                        set(Calendar.MONTH, month - 1)
                                        set(Calendar.DAY_OF_MONTH, 1)
                                    }
                                    if (pEmvCard.track2 == null) pEmvCard.track2 = EmvTrack2()
                                    pEmvCard.track2.expireDate = cal.time
                                } catch (e: Exception) {}
                            }
                        }
                    }
                    return pEmvCard.cardNumber != null
                }
            })

            val card = parser.readEmvCard()
            if (card == null || card.cardNumber == null) {
                return@withContext null
            }
            
            android.util.Log.d("NfcRead", "Card detected: ${card.cardNumber}")
            
            val expiryDate = card.expireDate
            val (month, year) = if (expiryDate != null) {
                val cal = Calendar.getInstance().apply { time = expiryDate }
                (cal.get(Calendar.MONTH) + 1) to (cal.get(Calendar.YEAR) % 100)
            } else null to null

            CardData(
                number = card.cardNumber?.filter { it.isDigit() } ?: "",
                expiryMonth = month,
                expiryYear = year
            )

        } catch (e: Exception) {
            null
        } finally {
            try { isoDep.close() } catch (_: Exception) {}
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
        val options = Bundle().apply {
            putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
        }
        adapter.enableReaderMode(
            this,
            nfcCallback,
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            options
        )
        if (state !is ReadState.Waiting) state = ReadState.Waiting
    }

    private fun onCardRead(card: CardData) {
        CardCache.card = card
        state = ReadState.Success(card)
        vibrate()
        if (card.number.isNotBlank()) {
            Clipboard.copy(this, "cardNumber", card.number)
            toast(R.string.read_copied)
        }
        if (notificationsGranted) {
            CardNotifier.show(this)
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

private class NfcProvider(private val isoDep: IsoDep) : IProvider {
    override fun transceive(pCommand: ByteArray): ByteArray {
        var lastException: IOException? = null
        repeat(3) {
            try {
                return isoDep.transceive(pCommand)
            } catch (e: IOException) {
                lastException = e
            }
        }
        throw CommunicationException(lastException?.message)
    }

    override fun getAt(): ByteArray {
        return isoDep.historicalBytes ?: isoDep.hiLayerResponse ?: byteArrayOf()
    }
}

@Composable
private fun ReadScreen(
    state: ReadState,
    onRetry: () -> Unit,
    onOpenNfcSettings: () -> Unit,
    onCopyNumber: (CardData) -> Unit,
    onCopyExpiry: (CardData) -> Unit,
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
