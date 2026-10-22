@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package my.belalang.booking.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Scaffold(containerColor = Neon.Bg) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 12.dp)) {
            if (selected == null) JobListScreen(vm, s) else JobDetailScreen(vm, s, selected)
        }
    }
}

// ---------- Blok binaan gaya V4 ----------

@Composable
private fun NeonCard(
    modifier: Modifier = Modifier,
    brush: Brush = Neon.Gradient,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val base = modifier.fillMaxWidth().clip(shape).background(Neon.Card).border(1.5.dp, brush, shape)
    Column(if (onClick != null) base.clickable(onClick = onClick) else base, content = content)
}

@Composable
private fun Badge(letter: String) {
    Box(Modifier.size(28.dp).background(Neon.Gradient, CircleShape), contentAlignment = Alignment.Center) {
        Text(letter, color = Neon.Bg, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    }
}

/** Modul bertanda huruf (A-F) yang boleh dikembang/dikuncupkan. */
@Composable
private fun Module(
    letter: String,
    title: String,
    summary: String = "",
    startOpen: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by rememberSaveable(letter) { mutableStateOf(startOpen) }
    NeonCard {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Badge(letter)
            Text(
                title, color = Neon.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                letterSpacing = 1.sp, modifier = Modifier.weight(1f),
            )
            if (summary.isNotEmpty()) Text(summary, color = Neon.Cyan, fontSize = 12.sp)
            Text(if (open) "▲" else "▼", color = Neon.Muted, fontSize = 12.sp)
        }
        if (open) {
            Column(
                Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Neon.Cyan,
    unfocusedBorderColor = Neon.Muted.copy(alpha = 0.5f),
    focusedLabelColor = Neon.Cyan,
    unfocusedLabelColor = Neon.Muted,
    cursorColor = Neon.Cyan,
    focusedTextColor = Neon.Text,
    unfocusedTextColor = Neon.Text,
)

@Composable
private fun NeonField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    singleLine: Boolean = false,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = singleLine, modifier = modifier, colors = fieldColors(),
    )
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Neon.Bg,
    labelColor = Neon.Muted,
    selectedContainerColor = Neon.Cyan.copy(alpha = 0.2f),
    selectedLabelColor = Neon.Cyan,
)

@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = Neon.Bg,
    checkedTrackColor = Neon.Cyan,
    uncheckedThumbColor = Neon.Muted,
    uncheckedTrackColor = Neon.Bg,
)

@Composable
private fun primaryButtonColors() = ButtonDefaults.buttonColors(
    containerColor = Neon.Cyan,
    contentColor = Neon.Bg,
    disabledContainerColor = Neon.Muted.copy(alpha = 0.25f),
    disabledContentColor = Neon.Muted,
)

@Composable
private fun ghostButtonColors() = ButtonDefaults.outlinedButtonColors(contentColor = Neon.Cyan)

// ---------- Skrin senarai ----------

@Composable
private fun JobListScreen(vm: BookingViewModel, s: UiState) {
    val rows = s.visibleRows
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
    ) {
        item { Header(vm, s) }
        item { ServiceModule(vm, s) }
        item { FareModule(vm, s) }
        item { TimeModule(vm, s) }
        item { CriteriaModule(vm, s) }
        item { ListHeader(vm, s, rows.size) }
        items(rows, key = { it.job.id }) { row -> JobCardView(row) { vm.select(row.job) } }
        item { ToolsModule(vm, s) }
    }
}

@Composable
private fun Header(vm: BookingViewModel, s: UiState) {
    val ready = StepConfig.ALL_IDS.count { s.configs[it]?.isUsable == true }
    val cfg = when {
        s.configLoading -> "Memuatkan konfigurasi..."
        s.configError != null -> "Konfigurasi gagal dimuat"
        else -> "Firestore: $ready/${StepConfig.ALL_IDS.size} step sedia"
    }
    NeonCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "BELALANG V", color = Neon.Cyan, fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp, letterSpacing = 2.sp,
                    )
                    Text(cfg, color = if (s.configError != null) Neon.Bad else Neon.Muted, fontSize = 12.sp)
                }
                Button(onClick = vm::refreshJobs, enabled = !s.listLoading, colors = primaryButtonColors()) {
                    Text(if (s.listLoading) "MEMUAT..." else "MUAT SENARAI", fontWeight = FontWeight.Bold)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Auto-refresh", color = Neon.Text, fontSize = 13.sp)
                Switch(checked = s.autoRefresh, onCheckedChange = vm::setAutoRefresh, colors = switchColors())
                Text("setiap ${s.intervalSec} saat", color = Neon.Muted, fontSize = 12.sp)
            }
            if (s.configError != null) Text(s.configError, color = Neon.Bad, fontSize = 12.sp)
            if (s.listStatus.isNotBlank()) Text(s.listStatus, color = Neon.Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ServiceModule(vm: BookingViewModel, s: UiState) {
    Module("A", "SERVIS", summary = s.serviceType.label, startOpen = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ServiceType.entries.forEach { t ->
                FilterChip(
                    selected = s.serviceType == t,
                    onClick = { vm.setServiceType(t) },
                    label = { Text(t.label) },
                    colors = chipColors(),
                )
            }
        }
    }
}

@Composable
private fun FareModule(vm: BookingViewModel, s: UiState) {
    val sum = when {
        s.fareMin.isBlank() && s.fareMax.isBlank() -> "Semua"
        else -> "RM${s.fareMin.ifBlank { "0" }} - ${s.fareMax.ifBlank { "∞" }}"
    }
    Module("B", "TAPISAN TAMBANG", summary = sum) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeonField(s.fareMin, vm::setFareMin, "Min (RM)", Modifier.weight(1f), singleLine = true)
            NeonField(s.fareMax, vm::setFareMax, "Maks (RM)", Modifier.weight(1f), singleLine = true)
        }
    }
}

private fun hourLabel(h: Int): String {
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "$h12 ${if (h < 12) "AM" else "PM"}"
}

@Composable
private fun TimeModule(vm: BookingViewModel, s: UiState) {
    Module("C", "MASA", summary = if (s.hours.isEmpty()) "Semua jam" else "${s.hours.size} jam dipilih") {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            FilterChip(
                selected = s.hours.isEmpty(), onClick = vm::clearHours,
                label = { Text("Semua jam") }, colors = chipColors(),
            )
            (0..23).forEach { h ->
                FilterChip(
                    selected = h in s.hours, onClick = { vm.toggleHour(h) },
                    label = { Text(hourLabel(h)) }, colors = chipColors(),
                )
            }
        }
    }
}

@Composable
private fun CriteriaModule(vm: BookingViewModel, s: UiState) {
    val n = listOf(s.includeLocations, s.avoidLocations, s.maxKm).count { it.isNotBlank() }
    Module("D", "CRITERIA LOKASI & JARAK", summary = if (n == 0) "Tiada" else "$n aktif") {
        NeonField(s.includeLocations, vm::setInclude, "Lokasi dikehendaki (asing dengan koma)")
        NeonField(s.avoidLocations, vm::setAvoid, "Lokasi dielakkan (asing dengan koma)")
        NeonField(s.maxKm, vm::setMaxKm, "Jarak maksimum (km)", singleLine = true)
    }
}

@Composable
private fun ListHeader(vm: BookingViewModel, s: UiState, shown: Int) {
    NeonCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Badge("E")
                Text(
                    "SENARAI JOB", color = Neon.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    letterSpacing = 1.sp, modifier = Modifier.weight(1f),
                )
                Text("$shown / ${s.jobs.size}", color = Neon.Cyan, fontWeight = FontWeight.Bold)
            }
            NeonField(s.query, vm::setQuery, "Cari", singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SortKey.entries.forEach { k ->
                    val arrow = if (s.sortKey == k) (if (s.ascending) " ↑" else " ↓") else ""
                    FilterChip(
                        selected = s.sortKey == k, onClick = { vm.setSort(k) },
                        label = { Text(k.label + arrow) }, colors = chipColors(),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sembunyi yang tak sesuai", color = Neon.Text, fontSize = 13.sp)
                Switch(checked = s.hideRejected, onCheckedChange = vm::setHideRejected, colors = switchColors())
            }
        }
    }
}

@Composable
private fun LocRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = Neon.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
        Text(value, color = Neon.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun JobCardView(row: JobRow, onClick: (() -> Unit)?) {
    val job = row.job
    val d = row.detail
    val (label, color) = when (row.eval.status) {
        JobStatus.SESUAI -> "SESUAI" to Neon.Ok
        JobStatus.PENDING -> "LULUS STEP 1" to Neon.Cyan
        JobStatus.TAK_SESUAI -> "TIDAK SESUAI" to Neon.Bad
        JobStatus.STEP1_GAGAL -> "GAGAL STEP 1" to Neon.Warn
    }
    NeonCard(onClick = onClick, brush = Brush.linearGradient(listOf(color, color.copy(alpha = 0.35f)))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label, color = color, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp,
                    modifier = Modifier
                        .background(color.copy(alpha = 0.16f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(job.id, color = Neon.Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(job.fare ?: "-", color = Neon.Cyan, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.weight(1f))
                Text(job.time ?: "-", color = Neon.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            job.title?.let { Text(it, color = Neon.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            job.serviceType?.let { Text(it, color = Neon.Muted, fontSize = 13.sp) }
            LocRow("PICKUP", d?.pickup ?: job.pickup)
            LocRow("DROP-OFF", d?.dropoff ?: job.dropoff)
            (d?.distanceKm ?: job.distanceKm)?.let { LocRow("JARAK", "$it km") }
            if (row.eval.reason.isNotBlank()) Text(row.eval.reason, color = color, fontSize = 12.sp)
        }
    }
}

/** F: tetapan auto-refresh dan LOG. */
@Composable
private fun ToolsModule(vm: BookingViewModel, s: UiState) {
    val clipboard = LocalClipboardManager.current
    val text = s.logs.takeLast(120).joinToString("\n") { "${it.time} ${it.text}" }
    Module("F", "AUTO-REFRESH & LOG", summary = "LOG ${s.logs.size}", startOpen = true) {
        Text("Selang: ${s.intervalSec} saat (min 5)", color = Neon.Muted, fontSize = 12.sp)
        Slider(
            value = s.intervalSec.toFloat(),
            onValueChange = { vm.setIntervalSec(it.toInt()) },
            valueRange = 5f..120f,
            colors = SliderDefaults.colors(
                thumbColor = Neon.Cyan,
                activeTrackColor = Neon.Cyan,
                inactiveTrackColor = Neon.Muted.copy(alpha = 0.3f),
            ),
        )
        StepConfig.ALL_IDS.forEach { id ->
            val c = s.configs[id]
            val st = when {
                c == null -> "tiada dokumen"
                !c.enabled -> "disabled"
                c.url.isBlank() -> "URL kosong"
                else -> "sedia"
            }
            Text("$id: $st", color = Neon.Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(text)) }, colors = ghostButtonColors()) { Text("Salin") }
            OutlinedButton(onClick = vm::clearLogs, colors = ghostButtonColors()) { Text("Kosongkan") }
            OutlinedButton(onClick = vm::loadConfigs, colors = ghostButtonColors()) { Text("Muat semula config") }
        }
        Box(
            Modifier.fillMaxWidth().height(240.dp)
                .background(Neon.Bg, RoundedCornerShape(12.dp))
                .padding(8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                if (text.isEmpty()) "(log kosong)" else text,
                color = Neon.Muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
            )
        }
    }
}

// ---------- Skrin butiran ----------

@Composable
private fun JobDetailScreen(vm: BookingViewModel, s: UiState, job: JobCard) {
    var askAccept by remember { mutableStateOf(false) }
    var askConfirm by remember { mutableStateOf(false) }
    val step4 = s.configs[StepConfig.STEP4]
    val step4On = step4?.isUsable == true
    val row = JobRow(job, s.evalOf(job), s.detailMap[job.id])

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.select(null) }) { Text("← KEMBALI", color = Neon.Cyan, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.weight(1f))
            Text("BUTIRAN JOB", color = Neon.Muted, fontSize = 12.sp, letterSpacing = 1.sp)
        }
        JobCardView(row, null)

        NeonCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("STEP 3 · ACCEPT", color = Neon.Text, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Button(
                    onClick = { askAccept = true }, enabled = !s.accept.running,
                    colors = primaryButtonColors(), modifier = Modifier.fillMaxWidth(),
                ) { Text("ACCEPT...", fontWeight = FontWeight.Bold) }
                ActionBox(s.accept.status, s.accept.body)
            }
        }

        NeonCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("STEP 4 · CONFIRM", color = Neon.Text, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Button(
                    onClick = { askConfirm = true },
                    enabled = step4On && s.accept.httpOk == true && !s.confirm.running,
                    colors = primaryButtonColors(), modifier = Modifier.fillMaxWidth(),
                ) { Text(if (step4On) "CONFIRM..." else "CONFIRM (DISABLED)", fontWeight = FontWeight.Bold) }
                if (!step4On) {
                    Text(
                        "Step 4 disabled. Hidupkan `enabled` dalam dokumen step4_future.",
                        color = Neon.Muted, fontSize = 12.sp,
                    )
                }
                ActionBox(s.confirm.status, s.confirm.body)
            }
        }
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
    Column(
        Modifier.fillMaxWidth().background(Neon.Bg, RoundedCornerShape(12.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (status.isNotEmpty()) Text(status, color = Neon.Cyan, fontSize = 12.sp)
        if (body.isNotEmpty()) Text(body.take(2_000), color = Neon.Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}
