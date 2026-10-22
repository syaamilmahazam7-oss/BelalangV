@file:OptIn(ExperimentalMaterial3Api::class)

package my.belalang.booking.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.belalang.booking.data.JobCard
import my.belalang.booking.data.ServiceType
import my.belalang.booking.data.StepConfig
import my.belalang.booking.domain.SortKey

@Composable
fun BookingApp(vm: BookingViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val selected = s.selected
    BackHandler(enabled = selected != null) { vm.select(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (selected == null) "BelalangV" else "Butiran job") })
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 12.dp)) {
            if (selected == null) JobListScreen(vm, s) else JobDetailScreen(vm, s, selected)
        }
    }
}

@Composable
private fun JobListScreen(vm: BookingViewModel, s: UiState) {
    val jobs = s.visibleJobs
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { ConfigBanner(vm, s) }
        item { ServiceTypeRow(vm, s) }
        item { FilterPanel(vm, s) }
        item { RefreshPanel(vm, s) }
        item { Text("${jobs.size} / ${s.jobs.size} job dipaparkan", style = MaterialTheme.typography.labelMedium) }
        items(jobs, key = { it.id }) { job -> JobCardView(job) { vm.select(job) } }
    }
}

@Composable
private fun ConfigBanner(vm: BookingViewModel, s: UiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Konfigurasi Firestore (api_configs)", style = MaterialTheme.typography.titleSmall)
            when {
                s.configLoading -> Text("Memuatkan...")
                s.configError != null -> Text(s.configError, color = MaterialTheme.colorScheme.error)
                else -> StepConfig.ALL_IDS.forEach { id ->
                    val c = s.configs[id]
                    val st = when {
                        c == null -> "tiada dokumen"
                        !c.enabled -> "disabled"
                        c.url.isBlank() -> "URL kosong"
                        else -> "sedia"
                    }
                    Text("$id: $st", style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedButton(onClick = vm::loadConfigs) { Text("Muat semula konfigurasi") }
        }
    }
}

@Composable
private fun ServiceTypeRow(vm: BookingViewModel, s: UiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ServiceType.entries.forEach { t ->
            FilterChip(
                selected = s.serviceType == t,
                onClick = { vm.setServiceType(t) },
                label = { Text(t.label) },
            )
        }
    }
}

@Composable
private fun FilterPanel(vm: BookingViewModel, s: UiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = s.query, onValueChange = vm::setQuery, label = { Text("Cari") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = s.includeLocations, onValueChange = vm::setInclude,
                label = { Text("Lokasi dikehendaki (asing dengan koma)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = s.avoidLocations, onValueChange = vm::setAvoid,
                label = { Text("Lokasi dielakkan (asing dengan koma)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = s.maxKm, onValueChange = vm::setMaxKm, label = { Text("Jarak maksimum (km)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SortKey.entries.forEach { k ->
                    val arrow = if (s.sortKey == k) (if (s.ascending) " ↑" else " ↓") else ""
                    FilterChip(
                        selected = s.sortKey == k,
                        onClick = { vm.setSort(k) },
                        label = { Text(k.label + arrow) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RefreshPanel(vm: BookingViewModel, s: UiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::refreshJobs, enabled = !s.listLoading) {
                    Text(if (s.listLoading) "Memuat..." else "Muat senarai")
                }
                Text("Auto-refresh", Modifier.padding(top = 10.dp))
                Switch(checked = s.autoRefresh, onCheckedChange = vm::setAutoRefresh)
            }
            Text("Selang: ${s.intervalSec} saat (min 5)", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = s.intervalSec.toFloat(),
                onValueChange = { vm.setIntervalSec(it.toInt()) },
                valueRange = 5f..120f,
            )
            Text(s.listStatus, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun JobCardView(job: JobCard, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(job.title ?: "Job ${job.id}", style = MaterialTheme.typography.titleSmall)
            job.pickup?.let { Text("Ambil: $it", style = MaterialTheme.typography.bodySmall) }
            job.dropoff?.let { Text("Hantar: $it", style = MaterialTheme.typography.bodySmall) }
            val extra = listOfNotNull(
                job.time, job.fare, job.distanceKm?.let { "$it km" }, job.serviceType,
            ).joinToString("  •  ")
            if (extra.isNotEmpty()) Text(extra, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun JobDetailScreen(vm: BookingViewModel, s: UiState, job: JobCard) {
    var askAccept by remember { mutableStateOf(false) }
    var askConfirm by remember { mutableStateOf(false) }
    val step4 = s.configs[StepConfig.STEP4]
    val step4On = step4?.isUsable == true

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        JobCardView(job) {}
        ActionBox("Step 2 - Butiran (baca sahaja)", s.details.status, s.details.body)

        Text("Step 3 - Accept", style = MaterialTheme.typography.titleSmall)
        Button(onClick = { askAccept = true }, enabled = !s.accept.running) { Text("Accept...") }
        ActionBox(null, s.accept.status, s.accept.body)

        Text("Step 4 - Confirm", style = MaterialTheme.typography.titleSmall)
        Button(
            onClick = { askConfirm = true },
            enabled = step4On && s.accept.httpOk == true && !s.confirm.running,
        ) { Text(if (step4On) "Confirm..." else "Confirm (disabled)") }
        if (!step4On) Text("Step 4 disabled. Hidupkan `enabled` dalam dokumen step4_future.", style = MaterialTheme.typography.bodySmall)
        ActionBox(null, s.confirm.status, s.confirm.body)

        OutlinedButton(onClick = { vm.select(null) }) { Text("Kembali") }
    }

    if (askAccept) {
        AlertDialog(
            onDismissRequest = { askAccept = false },
            title = { Text("Hantar Accept?") },
            text = { Text("Request Accept akan dihantar untuk job ${job.id}.") },
            confirmButton = { TextButton(onClick = { askAccept = false; vm.userPressedAccept() }) { Text("Hantar") } },
            dismissButton = { TextButton(onClick = { askAccept = false }) { Text("Batal") } },
        )
    }
    if (askConfirm) {
        AlertDialog(
            onDismissRequest = { askConfirm = false },
            title = { Text("Hantar Confirm?") },
            text = { Text("Request Confirm akan dihantar untuk job ${job.id}.") },
            confirmButton = { TextButton(onClick = { askConfirm = false; vm.userPressedConfirm() }) { Text("Hantar") } },
            dismissButton = { TextButton(onClick = { askConfirm = false }) { Text("Batal") } },
        )
    }
}

@Composable
private fun ActionBox(title: String?, status: String, body: String) {
    if (title == null && status.isEmpty() && body.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleSmall)
            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)
            if (body.isNotEmpty()) Text(body.take(2_000), style = MaterialTheme.typography.bodySmall)
        }
    }
}
