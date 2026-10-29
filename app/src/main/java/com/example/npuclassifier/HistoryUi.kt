package com.example.npuclassifier

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SearchPlaceholder(pad: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
        Text("Search coming soon", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun HistoryScreen(vm: SortViewModel, pad: PaddingValues) {
    val rows by vm.history.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        vm.history.value = Repo.historyPage(vm.ctx, 0, 100)
    }
    LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(rows) { r ->
            Column(Modifier.padding(8.dp)) {
                Text(r.label + "  " + r.conf)
                Text(r.destPath, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun MoreScreen(vm: SortViewModel, pad: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Categories", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        Text("Settings", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        Text("Diagnostics", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        Text("Background setup guide", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
    }
}