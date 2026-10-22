@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package my.belalang.booking.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.belalang.booking.data.JobCard
import my.belalang.booking.data.JobDetail
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
    val rows = s.visibleRows
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { ConfigBanner(vm, s) }
        item { ServiceTypeRow(vm, s) }
        item { Step1FilterPanel(vm, s) }
        item { CriteriaPanel(vm, s) }
        item { RefreshPanel(vm, s) }
        item { LogPanel(vm, s) }
        item { Text("${rows.size} dipaparkan / ${s.jobs.size} job", style = MaterialTheme.typography.labelMedium) }
        items(rows, key = { it.job.id }) { row -> JobCardView(row) { vm.select(row.job) } }
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

private fun hourLabel(h: Int): String {
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "$h12 ${if (h < 12) "AM" else "PM"}"
}

@Composable
private fun Step1FilterPanel(vm: BookingViewModel, s: UiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Tapisan Step 1 (servis, tambang, masa)", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = s.fareMin, onValueChange = vm::setFareMin, label = { Text("Tambang min (RM)") },
                    singleLine = true, modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = s.fareMax, onValueChange = vm::setFareMax, label = { Text("Tambang maks (RM)") },
                    singleLine = true, modifier = Modifier.weight(1f),
                )
            }
            Text("Jam pickup", style = MaterialTheme.typography.labelMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                FilterChip(selected = s.hours.isEmpty(), onClick = vm::clearHours, label = { Text("Semua jam") })
                (0..23).forEach { h ->
                    FilterChip(selected = h in s.hours, onClick = { vm.toggleHour(h) }, label = { Text(hourLabel(h)) })
                }
            }
        }
    }
}

@Composable
private fun CriteriaPanel(vm: BookingViewModel, s: UiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Criteria Step 2 (lokasi, jarak)", style = MaterialTheme.typography.titleSmall)
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
            OutlinedTextField(
                value = s.query, onValueChange = vm::setQuery, label = { Text("Cari") },
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sembunyi yang tak sesuai", Modifier.padding(top = 10.dp))
                Switch(checked = s.hideRejected, onCheckedChange = vm::setHideRejected)
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
private fun LogPanel(vm: BookingViewModel, s: UiState) {
    val clipboard = LocalClipboardManager.current
    val text = s.logs.takeLast(120).joinToString("\n") { "${it.time} ${it.text}" }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("LOG (${s.logs.size})", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(text)) }) { Text("Salin") }
                OutlinedButton(onClick = vm::clearLogs) { Text("Kosongkan") }
            }
            Box(Modifier.fillMaxWidth().height(240.dp).verticalScroll(rememberScrollState())) {
                Text(
                    if (text.isEmpty()) "(log kosong)" else text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun JobCardView(row: JobRow, onClick: () -> Unit) {
    val job = row.job
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(job.title ?: "Job ${job.id}", style = MaterialTheme.typography.titleSmall)
            val info = listOfNotNull(job.serviceType, job.fare, job.time).joinToString("  •  ")
            if (info.isNotEmpty()) Text(info, style = MaterialTheme.typography.labelMedium)
            DetailLines(row.detail)
            val status = when (row.eval.status) {
                JobStatus.SESUAI -> "SESUAI"
                JobStatus.PENDING -> "Menunggu Step 2: ${row.eval.reason}"
                JobStatus.TAK_SESUAI -> "Tak sesuai: ${row.eval.reason}"
                JobStatus.STEP1_GAGAL -> "Gagal Step 1: ${row.eval.reason}"
            }
            Text(status, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun DetailLines(d: JobDetail?) {
    if (d == null) return
    d.pickup?.let { Text("Ambil: $it", style = MaterialTheme.typography.bodySmall) }
    d.dropoff?.let { Text("Hantar: $it", style = MaterialTheme.typography.bodySmall) }
    d.distanceKm?.let { Text("Jarak: $it km", style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun JobDetailScreen(vm: BookingViewModel, s: UiState, job: JobCard) {
    var askAccept by remember { mutableStateOf(false) }
    var askConfirm by remember { mutableStateOf(false) }
    val step4 = s.configs[StepConfig.STEP4]
    val step4On = step4?.isUsable == true
    val row = JobRow(job, s.evalOf(job), s.detailMap[job.id])

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        JobCardView(row) {}

        Text("Step 3 - Accept", style = MaterialTheme.typography.titleSmall)
        Button(onClick = { askAccept = true }, enabled = !s.accept.running) { Text("Accept...") }
        ActionBox(s.accept.status, s.accept.body)

        Text("Step 4 - Confirm", style = MaterialTheme.typography.titleSmall)
        Button(
            onClick = { askConfirm = true },
            enabled = step4On && s.accept.httpOk == true && !s.confirm.running,
        ) { Text(if (step4On) "Confirm..." else "Confirm (disabled)") }
        if (!step4On) Text("Step 4 disabled. Hidupkan `enabled` dalam dokumen step4_future.", style = MaterialTheme.typography.bodySmall)
        ActionBox(s.confirm.status, s.confirm.body)

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
private fun ActionBox(status: String, body: String) {
    if (status.isEmpty() && body.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)
            if (body.isNotEmpty()) Text(body.take(2_000), style = MaterialTheme.typography.bodySmall)
        }
    }
}
