package com.example.npuclassifier

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.io.File
import kotlin.math.roundToInt

private val Amoled = darkColorScheme(
    primary = Color(0xFFB69CFF), onPrimary = Color(0xFF1B0B45),
    secondary = Color(0xFF7FDBCA), onSecondary = Color(0xFF00201B),
    background = Color.Black, onBackground = Color(0xFFEDE7FF),
    surface = Color(0xFF0B0B0F), onSurface = Color(0xFFEDE7FF),
    surfaceVariant = Color(0xFF17151F), onSurfaceVariant = Color(0xFFCAC4D6),
    primaryContainer = Color(0xFF2A2150), onPrimaryContainer = Color(0xFFE6DDFF),
    error = Color(0xFFFF6B6B)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Amoled, content = content)

private fun pct(v: Float) = "${(v * 100).roundToInt()}%"

@Composable
fun App(
    vm: SortViewModel,
    hasAll: Boolean,
    onPermission: () -> Unit,
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val snack = remember { SnackbarHostState() }
    val msg by vm.message.collectAsStateWithLifecycle()
    val list by vm.items.collectAsStateWithLifecycle()
    LaunchedEffect(msg) {
        msg?.let {
            snack.showSnackbar(it)
            vm.message.value = null
        }
    }
    val reviewCount = list.count { it.status == Status.REVIEW }
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("📷") }, label = { Text("Scan") })
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { BadgedBox(badge = { if (reviewCount > 0) Badge { Text("$reviewCount") } }) { Text("🔎") } },
                    label = { Text("Review") }
                )
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Text("⚙️") }, label = { Text("Settings") })
            }
        }
    ) { pad ->
        when (tab) {
            0 -> ScanScreen(vm, hasAll, onPermission, onPickFiles, onPickFolder, pad)
            1 -> ReviewScreen(vm, pad)
            else -> SettingsScreen(vm, pad)
        }
    }
}

@Composable
private fun SliderRow(
    title: String, value: Float, range: ClosedFloatingPointRange<Float>,
    label: String, steps: Int = 0, onChange: (Float) -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title)
            Text(label, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun Thumb(m: MediaItem, size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        if (m.isVideo) Text("🎞", fontSize = 22.sp)
        else AsyncImage(
            model = m.path?.let { File(it) } ?: m.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun ScanScreen(
    vm: SortViewModel, hasAll: Boolean, onPermission: () -> Unit,
    onPickFiles: () -> Unit, onPickFolder: () -> Unit, pad: PaddingValues
) {
    val list by vm.items.collectAsStateWithLifecycle()
    val cfg by vm.settings.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val prog by vm.progress.collectAsStateWithLifecycle()
    val canUndo by vm.canUndo.collectAsStateWithLifecycle()
    val auto = list.count { it.status == Status.AUTO }
    val review = list.count { it.status == Status.REVIEW }
    val pending = list.count { it.status == Status.PENDING || it.status == Status.ERROR }
    val stats = remember(list) {
        list.filter { it.backend != null && it.status != Status.ERROR }
            .groupBy { it.backend!! }
            .map { (b, l) -> "$b: ${l.size} files, ${l.map { it.ms }.average().roundToInt()} ms/file" }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(pad),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text("Sorter", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        if (!hasAll) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("All-files access is needed to create folders and copy or move files.")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onPermission) { Text("Grant access") }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPickFiles, enabled = !running) { Text("Pick files") }
                OutlinedButton(onClick = onPickFolder, enabled = !running) { Text("Pick folder") }
            }
        }
        item {
            Column {
                Text("Run on", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Backend.values().forEach { b ->
                        FilterChip(
                            selected = b in cfg.backends,
                            onClick = {
                                vm.updateSettings { s ->
                                    val n = if (b in s.backends) s.backends - b else s.backends + b
                                    if (n.isEmpty()) s else s.copy(backends = n)
                                }
                            },
                            label = { Text(b.name) }
                        )
                    }
                }
                if (cfg.backends.size > 1)
                    Text("Files are split across the selected processors.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SliderRow("Confidence needed", cfg.threshold, 0.1f..0.99f, "≥ ${pct(cfg.threshold)}") { v ->
                vm.updateSettings { it.copy(threshold = v) }
            }
        }
        if (prog.second > 0) item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(progress = { prog.first / prog.second.toFloat() }, modifier = Modifier.fillMaxWidth())
                Text("${prog.first}/${prog.second}  •  ready to sort $auto  •  needs review $review")
                stats.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { if (running) vm.stopScan() else vm.startScan() }, enabled = list.isNotEmpty()) {
                    Text(if (running) "Stop" else "Scan ($pending)")
                }
                FilledTonalButton(onClick = { vm.applyAuto() }, enabled = auto > 0 && !running) {
                    Text(if (cfg.move) "Move $auto" else "Copy $auto")
                }
                TextButton(onClick = { vm.undo() }, enabled = canUndo) { Text("Undo") }
            }
        }
        items(list, key = { it.media.uri.toString() }) { si -> ItemRow(si) }
    }
}

@Composable
private fun ItemRow(si: SortItem) {
    val cs = MaterialTheme.colorScheme
    Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(si.media, 56.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(si.media.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                val (txt, color) = when (si.status) {
                    Status.PENDING -> "Waiting" to cs.onSurfaceVariant
                    Status.AUTO -> "→ ${si.label} · ${pct(si.conf)}" to cs.secondary
                    Status.REVIEW -> "Unsure: ${si.label} · ${pct(si.conf)}" to cs.primary
                    Status.DONE -> "Done ${si.note}" to cs.secondary
                    Status.ERROR -> "Error: ${si.note}" to cs.error
                }
                Text(txt, color = color, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ReviewScreen(vm: SortViewModel, pad: PaddingValues) {
    val list by vm.items.collectAsStateWithLifecycle()
    val review = list.filter { it.status == Status.REVIEW }
    LazyColumn(
        Modifier.fillMaxSize().padding(pad),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Review (${review.size})", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "The app wasn't sure about these. Tap a suggestion or type a folder name. " +
                    "Learning from your choices arrives in the next update.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (review.isEmpty()) item { Text("Nothing to review 🎉") }
        items(review, key = { it.media.uri.toString() }) { si ->
            var custom by remember(si.media.uri) { mutableStateOf("") }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Thumb(si.media, 96.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(si.media.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        si.top.forEach { (raw, score) ->
                            val name = SortEngine.prettyLabel(raw)
                            AssistChip(onClick = { vm.assign(si, name) }, label = { Text("$name ${pct(score)}") })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = custom, onValueChange = { custom = it },
                            label = { Text("Other folder") }, singleLine = true, modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { vm.assign(si, custom.trim()) }, enabled = custom.isNotBlank()) { Text("Assign") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(vm: SortViewModel, pad: PaddingValues) {
    val cfg by vm.settings.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        SliderRow("Confidence needed", cfg.threshold, 0.1f..0.99f, "≥ ${pct(cfg.threshold)}") { v ->
            vm.updateSettings { it.copy(threshold = v) }
        }
        SliderRow("Gap to 2nd guess", cfg.minGap, 0f..0.5f, "≥ ${pct(cfg.minGap)}") { v ->
            vm.updateSettings { it.copy(minGap = v) }
        }
        Text("Too close to call → goes to Review.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("File action", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !cfg.move, onClick = { vm.updateSettings { it.copy(move = false) } }, label = { Text("Copy") })
            FilterChip(selected = cfg.move, onClick = { vm.updateSettings { it.copy(move = true) } }, label = { Text("Move") })
        }
        Text("Move only works for files found via \"Pick folder\". Files from \"Pick files\" are always copied.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = cfg.destRoot, onValueChange = { v -> vm.updateSettings { it.copy(destRoot = v) } },
            label = { Text("Sorted folder (videos go in …/Videos)") }, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = cfg.excludes, onValueChange = { v -> vm.updateSettings { it.copy(excludes = v) } },
            label = { Text("Exclude folders (comma separated names or /paths)") }, modifier = Modifier.fillMaxWidth()
        )
        SliderRow("Video: frame every", cfg.frameIntervalSec.toFloat(), 1f..10f, "${cfg.frameIntervalSec} s", steps = 8) { v ->
            vm.updateSettings { it.copy(frameIntervalSec = v.roundToInt()) }
        }
        SliderRow("Video: max frames", cfg.maxFrames.toFloat(), 2f..24f, "${cfg.maxFrames}", steps = 21) { v ->
            vm.updateSettings { it.copy(maxFrames = v.roundToInt()) }
        }
    }
}
