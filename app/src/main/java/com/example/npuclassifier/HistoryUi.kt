package com.example.npuclassifier

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

@Composable
fun SearchPlaceholder(pad: PaddingValues) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Long>>(emptyList()) }
    Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { results = Repo.ftsSearch(ctx, query, 50) }, modifier = Modifier.padding(top = 8.dp)) { Text("Search") }
        LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
            items(results) { id -> Text("id=$id") }
        }
    }
}

@Composable
fun HistoryScreen(vm: SortViewModel, pad: PaddingValues) {
    val ctx = LocalContext.current
    val rows by vm.history.collectAsStateWithLifecycle()
    var showMissing by remember { mutableStateOf(false) }
    var missingIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var changeTarget by remember { mutableStateOf<HistoryRow?>(null) }
    var newLabel by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        vm.history.value = Repo.historyPage(vm.ctx, 0, 100)
        val miss = Repo.historyPage(vm.ctx, 0, 100).filter { it.status == "ok" && !File(it.destPath).exists() }
        if (miss.isNotEmpty()) {
            missingIds = miss.map { it.id }
            showMissing = true
        }
    }

    changeTarget?.let { r ->
        AlertDialog(
            onDismissRequest = { changeTarget = null },
            title = { Text("Change category") },
            text = {
                Column {
                    Text("Current: ${r.label}")
                    androidx.compose.material3.OutlinedTextField(value = newLabel, onValueChange = { newLabel = it }, label = { Text("New category") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newLabel.isNotBlank()) vm.changeCategory(r.id, newLabel.trim())
                    changeTarget = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { changeTarget = null }) { Text("Cancel") } }
        )
    }
    if (showMissing) {
        AlertDialog(
            onDismissRequest = { showMissing = false },
            title = { Text("${missingIds.size} files are missing") },
            text = { Text("Some sorted files no longer exist.") },
            confirmButton = { TextButton(onClick = { vm.resolveMissing(missingIds, true); showMissing = false; vm.history.value = Repo.historyPage(vm.ctx, 0, 100) }) { Text("Remove all") } },
            dismissButton = { TextButton(onClick = { vm.resolveMissing(missingIds, false); showMissing = false }) { Text("Keep all") } }
        )
    }

    Column(Modifier.fillMaxSize().padding(pad)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                val uri = Uri.fromFile(File(ctx.cacheDir, "history.csv"))
                vm.exportCsv(uri)
            }) { Text("Export CSV") }
            TextButton(onClick = { vm.clearHistory() }) { Text("Clear history") }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(rows) { r ->
                Column(Modifier.fillMaxWidth().padding(8.dp)) {
                    Text(r.label + "  " + String.format("%.0f%%", r.conf * 100))
                    Text(r.destPath, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            try {
                                val f = File(r.destPath)
                                val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
                                ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(u, "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            } catch (e: Exception) { }
                        }) { Text("Open") }
                        TextButton(onClick = { vm.undoOne(r.id) }) { Text("Undo") }
                        TextButton(onClick = { changeTarget = r; newLabel = r.label }) { Text("Change") }
                    }
                }
            }
        }
    }
}

@Composable
fun MoreScreen(vm: SortViewModel, pad: PaddingValues) {
    var sub by remember { mutableStateOf("menu") }
    when (sub) {
        "categories" -> Column(Modifier.fillMaxSize()) {
            TextButton(onClick = { sub = "menu" }) { Text("← Back") }
            CategoriesScreen(vm, pad)
        }
        "settings" -> Column(Modifier.fillMaxSize()) {
            TextButton(onClick = { sub = "menu" }) { Text("← Back") }
            SettingsScreen(vm, pad)
        }
        "diagnostics" -> DiagnosticsScreen(vm, pad, onBack = { sub = "menu" })
        "setup" -> SetupGuideScreen(vm, pad, onBack = { sub = "menu" })
        else -> Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("More", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            androidx.compose.material3.Button(onClick = { sub = "categories" }, modifier = Modifier.fillMaxWidth()) { Text("Categories") }
            androidx.compose.material3.Button(onClick = { sub = "settings" }, modifier = Modifier.fillMaxWidth()) { Text("Settings") }
            androidx.compose.material3.Button(onClick = { sub = "diagnostics" }, modifier = Modifier.fillMaxWidth()) { Text("Diagnostics") }
            androidx.compose.material3.Button(onClick = { sub = "setup" }, modifier = Modifier.fillMaxWidth()) { Text("Background setup guide") }
            Text("People (coming soon)", color = androidx.compose.ui.graphics.Color.Gray)
        }
    }
}

@Composable
fun DiagnosticsScreen(vm: SortViewModel, pad: PaddingValues, onBack: (() -> Unit)? = null) {
    val lines = remember { mutableStateOf(AppLog.tail(300)) }
    Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
        onBack?.let { TextButton(onClick = it) { Text("← Back") } }
        Text("Diagnostics", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
        androidx.compose.material3.Button(onClick = { lines.value = AppLog.tail(300) }) { Text("Refresh") }
        androidx.compose.material3.Button(onClick = { }) { Text("Copy") }
        androidx.compose.material3.Button(onClick = { lines.value = emptyList() }) { Text("Clear log") }
        LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
            items(lines.value) { l -> Text(l, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
fun SetupGuideScreen(vm: SortViewModel, pad: PaddingValues, onBack: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        onBack?.let { TextButton(onClick = it) { Text("← Back") } }
        Text("Background setup (Xiaomi/HyperOS)", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
        Text("1. Ignore battery optimization")
        Text("2. Open Xiaomi autostart")
        Text("3. Set Battery saver to 'No restrictions' and lock app in Recents")
    }
}