package com.example.npuclassifier

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.material3.Switch
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

/** Tabs: 0 Scan, 1 Review, 2 Settings, 3 Categories. */
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
    val cats by vm.categories.collectAsStateWithLifecycle()
    LaunchedEffect(msg) {
        msg?.let {
            snack.showSnackbar(it)
            vm.message.value = null
        }
    }
    val ctx = vm.ctx
    LaunchedEffect(Unit) {
        while (true) {
            vm.items.value = Repo.page(ctx, Filter(processedOnly = true), 0, 50)
            kotlinx.coroutines.delay(1000)
        }
    }
    val reviewCount = list.count { it.status == Status.REVIEW }
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar(containerColor = Color.Black) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("📷") }, label = { Text("Scan") })
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { BadgedBox(badge = { if (reviewCount > 0) Badge { Text("$reviewCount") } }) { Text("🔎") } },
                    label = { Text("Review") }
                )
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Text("🔍") }, label = { Text("Search") })
                NavigationBarItem(selected = tab == 3, onClick = { tab = 3 }, icon = { Text("📜") }, label = { Text("History") })
                NavigationBarItem(selected = tab == 4, onClick = { tab = 4 }, icon = { Text("⋯") }, label = { Text("More") })
            }
        }
    ) { pad ->
        when (tab) {
            0 -> ScanScreen(vm, hasAll, onPermission, onPickFiles, onPickFolder, pad)
            1 -> ReviewScreen(vm, pad)
            2 -> SearchPlaceholder(pad)
            3 -> HistoryScreen(vm, pad)
            else -> MoreScreen(vm, pad)
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
private fun SwitchRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
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

/** A chip that is unmistakably on: filled primary, on-primary text, tick on the left. */
@Composable
private fun SelChip(selected: Boolean, label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
        leadingIcon = if (selected) {
            { Text("✓", color = cs.onPrimary, fontWeight = FontWeight.Bold) }
        } else null,
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = cs.primary,
            selectedLabelColor = cs.onPrimary
        )
    )
}

/** Small tag showing where a label came from: the user's categories or ImageNet. */
@Composable
private fun SourceTag(source: String) {
    if (source.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    val mine = source == "mine"
    val (txt, bg, fg) = if (mine) Triple("mine", cs.secondaryContainer, cs.onSecondaryContainer)
    else Triple("built-in", cs.primaryContainer, cs.onPrimaryContainer)
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 6.dp, vertical = 1.dp)) {
        Text(txt, color = fg, style = MaterialTheme.typography.labelSmall)
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
    // "after a scan" = at least one file has actually been classified.
    val scanned = list.any { it.status != Status.PENDING && it.status != Status.ERROR }
    var showSuggest by remember { mutableStateOf(false) }
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
                        SelChip(selected = b in cfg.backends, label = b.name, onClick = {
                            vm.updateSettings { s ->
                                val n = if (b in s.backends) s.backends - b else s.backends + b
                                if (n.isEmpty()) s else s.copy(backends = n)
                            }
                        })
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
                Text("Waiting: ${Repo.counts(vm.ctx).pending}", style = MaterialTheme.typography.bodySmall)
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
                TextButton(onClick = { vm.retryFailed() }, enabled = list.any { it.status == Status.ERROR } && !running) { Text("Retry failed") }
            }
        }
        item {
            FilledTonalButton(
                onClick = { vm.suggest(); showSuggest = true },
                enabled = scanned && !running,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Suggest categories") }
            if (!scanned) Text(
                "Run a scan first — suggestions come from the photos it looked at.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(list, key = { it.media.uri.toString() }) { si -> ItemRow(si) }
    }

    if (showSuggest) SuggestDialog(vm) { showSuggest = false }
}

@Composable
private fun ItemRow(si: SortItem) {
    val cs = MaterialTheme.colorScheme
    Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(si.media, 56.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        si.media.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false)
                    )
                    SourceTag(si.source)
                }
                val (txt, color) = when (si.status) {
                    Status.PENDING -> "Waiting" to cs.onSurfaceVariant
                    Status.RUNNING_FAST, Status.RUNNING_SMART -> "Processing" to cs.onSurfaceVariant
                    Status.AUTO -> "→ ${si.label} · ${pct(si.conf)}" to cs.secondary
                    Status.REVIEW -> "Unsure: ${si.label} · ${pct(si.conf)}" to cs.primary
                    Status.DONE -> "Done ${si.note}" to cs.secondary
                    Status.ERROR -> "Error: ${si.note}" to cs.error
                    Status.NEEDS_SMART -> "Needs smart" to cs.onSurfaceVariant
                    Status.MISSING -> "Missing" to cs.error
                }
                Text(txt, color = color, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ReviewScreen(vm: SortViewModel, pad: PaddingValues) {
    val reviewItems = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<SortItem>>(emptyList()) }
    val offset = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
    val loading = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        reviewItems.value = Repo.page(vm.ctx, Filter(statuses = setOf(Status.REVIEW)), 0, 100)
        offset.value = reviewItems.value.size
    }
    val review = reviewItems.value
    LazyColumn(
        Modifier.fillMaxSize().padding(pad),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Review (${review.size})", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "The app wasn't sure about these. Tap a suggestion or type a folder name.",
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
        item {
            TextButton(onClick = {
                if (loading.value) return@TextButton
                loading.value = true
                val page = Repo.page(vm.ctx, Filter(statuses = setOf(Status.REVIEW)), offset.value, 100)
                reviewItems.value = reviewItems.value + page
                offset.value += page.size
                loading.value = false
            }) { Text(if (loading.value) "Loading..." else "Load more") }
        }
    }
}

@Composable
fun CategoriesScreen(vm: SortViewModel, pad: PaddingValues, onBack: (() -> Unit)? = null) {
    val cats by vm.categories.collectAsStateWithLifecycle()
    var showNew by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var pickedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val pickExamples = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) pickedUris = uris
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(pad),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Categories (${cats.size})", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Button(onClick = { newName = ""; pickedUris = emptyList(); showNew = true }) { Text("New category") }
            }
        }
        if (cats.isEmpty()) item {
            Text("No categories yet. Create one or tap 'Suggest categories' on the Scan tab.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(cats, key = { it.name }) { cat ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(cat.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text("${cat.examples.size} example${if (cat.examples.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = { vm.deleteCategory(cat.name) }) { Text("Delete") }
                }
            }
        }
    }

    if (showNew) AlertDialog(
        onDismissRequest = { showNew = false },
        title = { Text("New category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Name") }, singleLine = true)
                Button(onClick = { pickExamples.launch(arrayOf("image/*")) }) { Text("Pick example photos (${pickedUris.size})") }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (newName.isNotBlank() && pickedUris.isNotEmpty()) {
                    vm.createCategory(newName.trim(), pickedUris)
                    showNew = false
                }
            }, enabled = newName.isNotBlank() && pickedUris.isNotEmpty()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = { showNew = false }) { Text("Cancel") } }
    )
}

@Composable
fun SettingsScreen(vm: SortViewModel, pad: PaddingValues, onBack: (() -> Unit)? = null) {
    val cfg by vm.settings.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Model", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelChip(selected = cfg.model == "mnet", label = "MobileNet (fast)", onClick = { vm.updateSettings { it.copy(model = "mnet") } })
            SelChip(selected = cfg.model == "clip", label = "CLIP (smart)", onClick = { vm.updateSettings { it.copy(model = "clip") } })
        }
        Text("MobileNet: fast, bundled, ImageNet labels. CLIP: slower, understands words (zero-shot).",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            SelChip(selected = !cfg.move, label = "Copy", onClick = { vm.updateSettings { it.copy(move = false) } })
            SelChip(selected = cfg.move, label = "Move", onClick = { vm.updateSettings { it.copy(move = true) } })
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
        Text("Video depth", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelChip(selected = cfg.videoDepth == 0, label = "Quick (8)", onClick = { vm.updateSettings { it.copy(videoDepth = 0) } })
            SelChip(selected = cfg.videoDepth == 1, label = "Balanced (24)", onClick = { vm.updateSettings { it.copy(videoDepth = 1) } })
            SelChip(selected = cfg.videoDepth == 2, label = "Thorough", onClick = { vm.updateSettings { it.copy(videoDepth = 2) } })
        }

        Text("Categories", style = MaterialTheme.typography.labelLarge)
        SliderRow("Category match needed", cfg.catThreshold, 0.5f..0.99f, "≥ ${pct(cfg.catThreshold)}") { v ->
            vm.updateSettings { it.copy(catThreshold = v) }
        }
        SliderRow("Category gap", cfg.catGap, 0f..0.2f, "≥ ${pct(cfg.catGap)}") { v ->
            vm.updateSettings { it.copy(catGap = v) }
        }
        SwitchRow("Use my categories", "Sort using your taught categories (highest priority).",
            cfg.useMine) { v -> vm.updateSettings { it.copy(useMine = v) } }
        SwitchRow("Use built-in labels", "Fall back to ImageNet labels when no category matches.",
            cfg.useBuiltin) { v -> vm.updateSettings { it.copy(useBuiltin = v) } }
        Text("Scan mode", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelChip(selected = cfg.scanMode == "auto", label = "Auto", onClick = { vm.updateSettings { it.copy(scanMode = "auto") } })
            SelChip(selected = cfg.scanMode == "fast", label = "Fastest", onClick = { vm.updateSettings { it.copy(scanMode = "fast") } })
            SelChip(selected = cfg.scanMode == "smart", label = "Smart", onClick = { vm.updateSettings { it.copy(scanMode = "smart") } })
        }
        Text("OCR mode", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelChip(selected = cfg.ocrMode == "off", label = "Off", onClick = { vm.updateSettings { it.copy(ocrMode = "off") } })
            SelChip(selected = cfg.ocrMode == "docs", label = "Docs", onClick = { vm.updateSettings { it.copy(ocrMode = "docs") } })
            SelChip(selected = cfg.ocrMode == "all", label = "All", onClick = { vm.updateSettings { it.copy(ocrMode = "all") } })
            SelChip(selected = cfg.ocrMode == "search", label = "Search", onClick = { vm.updateSettings { it.copy(ocrMode = "search") } })
        }
        Text("Hindi OCR", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelChip(selected = cfg.ocrHindi == "auto", label = "Auto", onClick = { vm.updateSettings { it.copy(ocrHindi = "auto") } })
            SelChip(selected = cfg.ocrHindi == "on", label = "On", onClick = { vm.updateSettings { it.copy(ocrHindi = "on") } })
            SelChip(selected = cfg.ocrHindi == "off", label = "Off", onClick = { vm.updateSettings { it.copy(ocrHindi = "off") } })
        }
        SwitchRow("Tags", "Run ML Kit image labeling and save tags.", cfg.tagsOn) { v -> vm.updateSettings { it.copy(tagsOn = v) } }
        Text("Search model", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelChip(selected = cfg.searchModel == "clip", label = "CLIP", onClick = { vm.updateSettings { it.copy(searchModel = "clip") } })
            SelChip(selected = cfg.searchModel == "mnet", label = "MobileNet", onClick = { vm.updateSettings { it.copy(searchModel = "mnet") } })
        }
        SwitchRow("Index while charging", "Build search embeddings only while charging.", cfg.indexWhileCharging) { v -> vm.updateSettings { it.copy(indexWhileCharging = v) } }
        Text("Auto-apply after scan", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelChip(selected = cfg.autoApply == "off", label = "Off", onClick = { vm.updateSettings { it.copy(autoApply = "off") } })
            SelChip(selected = cfg.autoApply == "ask", label = "Ask", onClick = { vm.updateSettings { it.copy(autoApply = "ask") } })
            SelChip(selected = cfg.autoApply == "on", label = "On", onClick = { vm.updateSettings { it.copy(autoApply = "on") } })
        }
        SwitchRow("Faces", "Detect and match faces.", cfg.facesOn) { v -> vm.updateSettings { it.copy(facesOn = v) } }
        Text("Background setup guide", style = MaterialTheme.typography.labelLarge)
        Text("To scan in background: enable Display over other apps and disable battery optimization for this app in Android settings.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Diagnostics", style = MaterialTheme.typography.labelLarge)
        Text("See Diagnostics tab for stage speed (files/s).",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SuggestDialog(vm: SortViewModel, onDismiss: () -> Unit) {
    val clusters by vm.suggestions.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Suggested categories") },
        text = {
            if (clusters.isEmpty()) {
                Text("No clusters found. Need more photos or lower similarity.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    clusters.forEach { cl ->
                        var name by remember(cl) { mutableStateOf(cl.suggestedName) }
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                Text("${cl.members.size} photos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    cl.members.take(4).forEach { m -> Thumb(m.media, 48.dp) }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { vm.approveCluster(cl, name.trim().ifBlank { cl.suggestedName }) }) { Text("Approve") }
                                    OutlinedButton(onClick = { vm.dismissCluster(cl) }) { Text("Skip") }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
