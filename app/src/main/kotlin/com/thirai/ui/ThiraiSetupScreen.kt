package com.thirai.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.thirai.config.Show
import com.thirai.config.ThiraiConfigFetcher
import com.thirai.tv.TvController
import com.thirai.tv.TvPairing
import kotlinx.coroutines.launch

/** How the TV link reads at a glance. */
private enum class Link { None, Connecting, Connected, Failed }

/** Which step of the pairing flow (if any) is on screen. */
private enum class PairPhase { None, Connecting, EnterPin, Finishing }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThiraiSetupScreen() {
    val context = LocalContext.current
    val controller = remember { TvController(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var tvName by remember { mutableStateOf(controller.tvName) }
    var link by remember { mutableStateOf(if (controller.manualHost != null) Link.Connecting else Link.None) }

    // Pairing flow.
    var pairPhase by remember { mutableStateOf(PairPhase.None) }
    var pin by remember { mutableStateOf("") }
    var pairError by remember { mutableStateOf<String?>(null) }
    val pairing = remember { mutableStateOf<TvPairing?>(null) }

    // Discovery ("Find my TV").
    var discovering by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    val discovered = remember { mutableStateListOf<TvController.DiscoveredTv>() }

    // Shows.
    val shows: SnapshotStateList<Show> = remember { mutableStateListOf() }
    var showsLoading by remember { mutableStateOf(true) }
    var showsError by remember { mutableStateOf(false) }
    var sendingIndex by remember { mutableIntStateOf(-1) }

    // Show source (config URL) + QR.
    var sourceUrl by remember { mutableStateOf(ThiraiConfigFetcher.sourceUrl(context)) }
    var showQr by remember { mutableStateOf(false) }

    fun loadShows() {
        showsLoading = true
        showsError = false
        scope.launch {
            val fetched = ThiraiConfigFetcher.fetchShows(context)
            shows.clear()
            shows.addAll(fetched)
            showsError = fetched.isEmpty()
            showsLoading = false
        }
    }

    fun runTest() {
        if (link == Link.Connecting && controller.manualHost == null) return
        link = Link.Connecting
        scope.launch {
            link = if (controller.testConnection()) Link.Connected else Link.Failed
        }
    }

    fun beginPairing(host: String) {
        pairError = null
        pin = ""
        pairPhase = PairPhase.Connecting
        val session = controller.newPairing(host)
        pairing.value = session
        scope.launch {
            try {
                session.start()
                pairPhase = PairPhase.EnterPin
            } catch (e: Exception) {
                session.close()
                pairing.value = null
                pairPhase = PairPhase.None
                link = Link.Failed
                Toast.makeText(
                    context,
                    "Couldn't reach the TV to pair: ${e.message ?: "connection failed"}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    fun submitPin() {
        val session = pairing.value ?: return
        pairPhase = PairPhase.Finishing
        pairError = null
        scope.launch {
            try {
                session.finish(pin.trim())
                controller.markPaired()
                pairPhase = PairPhase.None
                session.close()
                pairing.value = null
                pin = ""
                runTest()
            } catch (e: Exception) {
                pairError = e.message ?: "Pairing failed"
                pairPhase = PairPhase.EnterPin
            }
        }
    }

    fun cancelPairing() {
        pairing.value?.close()
        pairing.value = null
        pairPhase = PairPhase.None
        pin = ""
        pairError = null
    }

    fun findTvs() {
        if (discovering) return
        discovering = true
        scope.launch {
            val list = controller.discoverTvs()
            discovered.clear()
            discovered.addAll(list)
            discovering = false
            showPicker = true
        }
    }

    fun selectTv(tv: TvController.DiscoveredTv) {
        controller.rememberTv(tv.name, tv.host)
        tvName = tv.name
        showPicker = false
        // A freshly picked TV needs the one-time pairing.
        beginPairing(tv.host)
    }

    fun play(show: Show, index: Int) {
        if (show.deep_link.isBlank() || sendingIndex != -1) return
        sendingIndex = index
        Toast.makeText(context, "Playing “${show.displayTitle}” on the TV…", Toast.LENGTH_SHORT).show()
        scope.launch {
            val ok = controller.play(show.deep_link, show.app_package, show.home_link)
            sendingIndex = -1
            if (!ok) {
                Toast.makeText(
                    context,
                    if (tvName != null) "Couldn't reach the TV. Is it on?" else "Find your TV first.",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    fun applySource(url: String) {
        val trimmed = url.trim()
        sourceUrl = trimmed
        ThiraiConfigFetcher.setSourceUrl(context, trimmed)
        loadShows()
        scope.launch { updateWidgets(context) }
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.trim()?.takeIf { it.isNotBlank() }?.let { scanned ->
            applySource(scanned)
            Toast.makeText(context, "Show source updated from QR", Toast.LENGTH_SHORT).show()
        }
    }

    // On first appearance, pull the shows and — if a TV is already set up —
    // confirm it's reachable so the card reads true.
    LaunchedEffect(Unit) {
        loadShows()
        if (controller.manualHost != null) runTest()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { ThiraiWordmark() },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            TvCard(link = link, tvName = tvName, tvHost = controller.manualHost)

            Spacer(Modifier.height(14.dp))

            SecondaryButton(
                text = when {
                    discovering -> "Searching…"
                    tvName == null -> "Find my TV"
                    else -> "Connect a different TV"
                },
                enabled = !discovering,
                onClick = { findTvs() },
            )

            Spacer(Modifier.height(28.dp))

            // ---- Shows ----
            Eyebrow("Shows")
            Spacer(Modifier.height(10.dp))
            ShowsSection(
                loading = showsLoading,
                error = showsError,
                shows = shows,
                sendingIndex = sendingIndex,
                onPlay = ::play,
            )
            Spacer(Modifier.height(12.dp))
            SecondaryButton(
                text = "Refresh from cloud",
                enabled = !showsLoading,
                onClick = {
                    loadShows()
                    scope.launch { updateWidgets(context) }
                },
            )
            Text(
                text = "Tap a show to play it on the TV. Shows are managed remotely in the source below — edit it once and every phone picks up the change on the next refresh.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(Modifier.height(28.dp))

            // ---- Show source ----
            Eyebrow("Show source")
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = sourceUrl,
                onValueChange = { sourceUrl = it },
                label = { Text("Config URL") },
                placeholder = { Text("https://…/shows.json") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                supportingText = {
                    Text("Any reachable JSON URL — a gist, a repo, your own host. Share it to another phone with a QR.")
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TonalButton(
                    text = "Save & refresh",
                    onClick = { applySource(sourceUrl) },
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    text = "Scan QR",
                    onClick = {
                        scanLauncher.launch(
                            ScanOptions().apply {
                                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                setPrompt("Scan a Thirai show-source QR")
                                setBeepEnabled(false)
                                setOrientationLocked(true)
                                captureActivity = PortraitCaptureActivity::class.java
                            },
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                text = "Share as QR",
                onClick = { showQr = true },
            )

            Spacer(Modifier.height(32.dp))
        }
    }

    if (pairPhase == PairPhase.EnterPin || pairPhase == PairPhase.Finishing) {
        PinDialog(
            pin = pin,
            onPinChange = { pin = it.filter { c -> c.isLetterOrDigit() }.take(6).uppercase() },
            error = pairError,
            busy = pairPhase == PairPhase.Finishing,
            onConfirm = { submitPin() },
            onDismiss = { cancelPairing() },
        )
    }

    if (showPicker) {
        TvPickerDialog(
            tvs = discovered,
            onSelect = { selectTv(it) },
            onRetry = { showPicker = false; findTvs() },
            onDismiss = { showPicker = false },
        )
    }

    if (showQr) {
        SourceQrDialog(url = sourceUrl, onDismiss = { showQr = false })
    }
}

/**
 * The TV card: a status dot and the connected TV's name, so the app's link to
 * the television reads at a glance. Replaces the old generic "TV connected" row.
 */
@Composable
private fun TvCard(link: Link, tvName: String?, tvHost: String?) {
    // Prefer the friendly name (from Find my TV); fall back to the address for a
    // TV that was set up before names were captured.
    val label = tvName ?: tvHost
    val dotColor: Color = when (link) {
        Link.Connected -> MaterialTheme.colorScheme.primary
        Link.Failed -> MaterialTheme.colorScheme.error
        Link.Connecting -> MaterialTheme.colorScheme.primary
        Link.None -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val headline = when (link) {
        Link.Connected -> label ?: "TV connected"
        Link.Failed -> label ?: "TV unreachable"
        Link.Connecting -> "Connecting…"
        Link.None -> "No TV yet"
    }
    val detail = when (link) {
        Link.Connected -> "Connected and ready."
        Link.Failed -> "Not reachable — make sure it's on and on the same Wi-Fi."
        Link.Connecting -> label?.let { "Reaching $it…" } ?: "Reaching the TV…"
        Link.None -> "Tap “Find my TV” to connect one."
    }

    ThiraiCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (link == Link.Connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                StatusDot(color = dotColor, contentDescription = headline)
            }
            Spacer(Modifier.size(12.dp))
            Column {
                Text(headline, style = MaterialTheme.typography.titleMedium)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PinDialog(
    pin: String,
    onPinChange: (String) -> Unit,
    error: String?,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Enter the code from your TV") },
        text = {
            Column {
                Text(
                    "Your TV is showing a 6-character pairing code. Type it here to link Thirai to it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = onPinChange,
                    label = { Text("Pairing code") },
                    placeholder = { Text("e.g. 4F7A2C") },
                    singleLine = true,
                    enabled = !busy,
                    isError = error != null,
                    supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy && pin.length == 6) {
                Text(if (busy) "Pairing…" else "Pair")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        },
    )
}

@Composable
private fun TvPickerDialog(
    tvs: List<TvController.DiscoveredTv>,
    onSelect: (TvController.DiscoveredTv) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tvs.isEmpty()) "No TVs found" else "Pick your TV") },
        text = {
            if (tvs.isEmpty()) {
                Text(
                    "Make sure the TV is on and on the same Wi-Fi as this phone, then try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column {
                    tvs.forEach { tv ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(tv) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tv.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    tv.host,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRetry) { Text("Search again") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun SourceQrDialog(url: String, onDismiss: () -> Unit) {
    val qr = remember(url) {
        runCatching {
            BarcodeEncoder().encodeBitmap(url, BarcodeFormat.QR_CODE, 720, 720)
        }.getOrNull()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share show source") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (qr != null) {
                    Image(
                        bitmap = qr.asImageBitmap(),
                        contentDescription = "Show source QR",
                        modifier = Modifier.size(220.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "On another phone: Show source → Scan QR. It copies this URL so both phones show the same list.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("Couldn't make a QR for this URL.")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun ShowsSection(
    loading: Boolean,
    error: Boolean,
    shows: List<Show>,
    sendingIndex: Int,
    onPlay: (Show, Int) -> Unit,
) {
    when {
        loading -> ThiraiCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(12.dp))
                Text("Loading show list…", style = MaterialTheme.typography.bodyMedium)
            }
        }
        error || shows.isEmpty() -> ThiraiCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "No shows configured yet. Point the source below at your list, then refresh.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            shows.forEachIndexed { index, show ->
                ShowRow(
                    show = show,
                    sending = sendingIndex == index,
                    enabled = sendingIndex == -1 && show.deep_link.isNotBlank(),
                    onClick = { onPlay(show, index) },
                )
            }
        }
    }
}

@Composable
private fun ShowRow(
    show: Show,
    sending: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ThiraiCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 52.dp, height = 70.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(com.thirai.R.drawable.ic_logo),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(22.dp),
                )
                AsyncImage(
                    model = show.image_url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    show.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (sending) "Sending to TV…" else "Tap to play on TV",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (sending) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Spacer(Modifier.size(12.dp))
            PlayAffordance(sending = sending)
        }
    }
}

/** Trailing amber play button, swapped for a spinner while a send is in flight. */
@Composable
private fun PlayAffordance(sending: Boolean) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (sending) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Play",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** Broadcast an update so placed home-screen widgets redraw with fresh shows. */
private fun updateWidgets(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    val ids = manager.getAppWidgetIds(
        ComponentName(context, com.thirai.widget.ThiraiWidgetProvider::class.java),
    )
    if (ids.isEmpty()) return
    val intent = Intent(context, com.thirai.widget.ThiraiWidgetProvider::class.java).apply {
        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
    }
    context.sendBroadcast(intent)
}
