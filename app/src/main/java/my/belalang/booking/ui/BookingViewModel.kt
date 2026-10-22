package my.belalang.booking.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import my.belalang.booking.data.ApiClient
import my.belalang.booking.data.ApiResponse
import my.belalang.booking.data.ConfigRepository
import my.belalang.booking.data.JobCard
import my.belalang.booking.data.JobDetail
import my.belalang.booking.data.JobParser
import my.belalang.booking.data.ServiceType
import my.belalang.booking.data.StepConfig
import my.belalang.booking.domain.Criteria
import my.belalang.booking.domain.JobFilterEngine
import my.belalang.booking.domain.SortKey
import my.belalang.booking.domain.Step1Filter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ActionState(
    val running: Boolean = false,
    val status: String = "",
    val body: String = "",
    /** null = belum cuba; true = HTTP 2xx diterima; false = gagal. Ini BUKAN bukti hasil sebenar. */
    val httpOk: Boolean? = null,
)

data class LogEntry(val time: String, val text: String)

enum class JobStatus { SESUAI, PENDING, TAK_SESUAI, STEP1_GAGAL }

data class JobEval(val status: JobStatus, val reason: String)

data class JobRow(val job: JobCard, val eval: JobEval, val detail: JobDetail?)

data class UiState(
    val configs: Map<String, StepConfig> = emptyMap(),
    val configError: String? = null,
    val configLoading: Boolean = false,

    val jobs: List<JobCard> = emptyList(),
    /** Butiran Step 2 yang berjaya diambil, mengikut booking ID. */
    val detailMap: Map<String, JobDetail> = emptyMap(),
    val step2Errors: Map<String, String> = emptyMap(),
    val listStatus: String = "Belum dimuat.",
    val listLoading: Boolean = false,

    // Tapisan Step 1
    val serviceType: ServiceType = ServiceType.NORMAL,
    val fareMin: String = "",
    val fareMax: String = "",
    val hours: Set<Int> = emptySet(),
    // Criteria Step 2
    val includeLocations: String = "",
    val avoidLocations: String = "",
    val maxKm: String = "",
    // Paparan
    val query: String = "",
    val hideRejected: Boolean = true,
    val sortKey: SortKey = SortKey.DISTANCE,
    val ascending: Boolean = true,

    val autoRefresh: Boolean = false,
    val intervalSec: Int = 30,

    val logs: List<LogEntry> = emptyList(),

    val selected: JobCard? = null,
    val accept: ActionState = ActionState(),
    val confirm: ActionState = ActionState(),
) {
    val step1Filter: Step1Filter
        get() = Step1Filter(
            serviceType = serviceType,
            kliaKeywords = configs[StepConfig.STEP1]?.kliaKeywords ?: emptyList(),
            fareMin = fareMin.trim().toDoubleOrNull(),
            fareMax = fareMax.trim().toDoubleOrNull(),
            hours = hours,
        )

    val criteria: Criteria
        get() = Criteria(
            includeLocations = JobFilterEngine.splitList(includeLocations),
            avoidLocations = JobFilterEngine.splitList(avoidLocations),
            maxKm = maxKm.trim().toDoubleOrNull(),
        )

    fun evalOf(job: JobCard): JobEval {
        val d1 = JobFilterEngine.checkStep1(job, step1Filter)
        if (!d1.pass) return JobEval(JobStatus.STEP1_GAGAL, d1.reason)
        val detail = detailMap[job.id]
            ?: return JobEval(JobStatus.PENDING, step2Errors[job.id] ?: "menunggu Step 2")
        val d2 = JobFilterEngine.checkCriteria(detail, criteria)
        return JobEval(if (d2.pass) JobStatus.SESUAI else JobStatus.TAK_SESUAI, d2.reason)
    }

    val visibleRows: List<JobRow>
        get() {
            val q = query.trim()
            val rows = jobs.map { JobRow(it, evalOf(it), detailMap[it.id]) }
                .filter { it.eval.status != JobStatus.STEP1_GAGAL }
                .filter { !(hideRejected && it.eval.status == JobStatus.TAK_SESUAI) }
                .filter { q.isEmpty() || it.job.searchText.contains(q, ignoreCase = true) }
            val sorted = when (sortKey) {
                SortKey.DISTANCE -> rows.sortedBy { it.detail?.distanceKm ?: Double.MAX_VALUE }
                SortKey.FARE -> rows.sortedBy { JobParser.extractNumber(it.job.fare) ?: Double.MAX_VALUE }
                SortKey.TIME -> rows.sortedBy { it.job.time ?: "￿" }
            }
            val dir = if (ascending) sorted else sorted.reversed()
            // SESUAI paling atas, kemudian menunggu, kemudian tak sesuai (sort stabil).
            return dir.sortedBy {
                when (it.eval.status) {
                    JobStatus.SESUAI -> 0
                    JobStatus.PENDING -> 1
                    else -> 2
                }
            }
        }
}

class BookingViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ConfigRepository(app)
    private val api = ApiClient()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private var autoJob: Job? = null

    init {
        // Buka app -> baca konfigurasi Firestore -> auto-fetch Step 1 sekali (baca sahaja).
        viewModelScope.launch {
            _state.update { it.copy(configLoading = true) }
            val r = repo.load()
            _state.update { it.copy(configs = r.configs, configError = r.error, configLoading = false) }
            if (r.error == null) {
                log("Konfigurasi Firestore dimuat: ${r.configs.keys.sorted().joinToString(", ")}")
                runCycle()
            } else {
                log("Konfigurasi GAGAL: ${r.error}")
            }
        }
    }

    // ---- Log (tiada header/body request, tiada token) --------------------------------------
    private fun log(text: String) {
        val t = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _state.update { it.copy(logs = (it.logs + LogEntry(t, text)).takeLast(MAX_LOG)) }
    }

    fun clearLogs() = _state.update { it.copy(logs = emptyList()) }

    // ---- Konfigurasi -------------------------------------------------------------------------
    fun loadConfigs() {
        viewModelScope.launch {
            _state.update { it.copy(configLoading = true) }
            val r = repo.load()
            _state.update { it.copy(configs = r.configs, configError = r.error, configLoading = false) }
            log(if (r.error == null) "Konfigurasi dimuat semula." else "Konfigurasi GAGAL: ${r.error}")
        }
    }

    // ---- Penapis (UI sahaja) -----------------------------------------------------------------
    fun setServiceType(t: ServiceType) = _state.update { it.copy(serviceType = t) }
    fun setFareMin(v: String) = _state.update { it.copy(fareMin = v) }
    fun setFareMax(v: String) = _state.update { it.copy(fareMax = v) }
    fun toggleHour(h: Int) = _state.update {
        it.copy(hours = if (h in it.hours) it.hours - h else it.hours + h)
    }
    fun clearHours() = _state.update { it.copy(hours = emptySet()) }
    fun setQuery(v: String) = _state.update { it.copy(query = v) }
    fun setInclude(v: String) = _state.update { it.copy(includeLocations = v) }
    fun setAvoid(v: String) = _state.update { it.copy(avoidLocations = v) }
    fun setMaxKm(v: String) = _state.update { it.copy(maxKm = v) }
    fun setHideRejected(v: Boolean) = _state.update { it.copy(hideRejected = v) }
    fun setSort(k: SortKey) = _state.update {
        if (it.sortKey == k) it.copy(ascending = !it.ascending) else it.copy(sortKey = k, ascending = true)
    }

    // ---- Pusingan: Step 1 (senarai) -> tapisan -> Step 2 (butiran) -> criteria ---------------
    fun refreshJobs() {
        viewModelScope.launch { runCycle() }
    }

    private suspend fun runCycle() {
        if (_state.value.listLoading) return
        _state.update { it.copy(listLoading = true) }
        try {
            cycle()
        } finally {
            _state.update { it.copy(listLoading = false) }
        }
    }

    private suspend fun cycle() {
        val cfg1 = _state.value.configs[StepConfig.STEP1]
        if (cfg1 == null) {
            val m = "Step 1: dokumen ${StepConfig.STEP1} tiada dalam Firestore."
            log(m); _state.update { it.copy(listStatus = m) }
            return
        }
        if (!cfg1.isUsable) {
            val m = "Step 1: disabled atau URL kosong."
            log(m); _state.update { it.copy(listStatus = m) }
            return
        }

        log("--- Pusingan baharu ---")
        val typeValue = cfg1.serviceTypeValues[_state.value.serviceType.name.lowercase()].orEmpty()
        val resp = api.execute(cfg1, mapOf("serviceType" to typeValue))
        if (resp.error != null || !resp.isHttpSuccess) {
            val m = "Step 1: GAGAL - ${resp.error ?: "HTTP ${resp.code}"}"
            log(m); _state.update { it.copy(listStatus = m) }
            return
        }
        val parsed = JobParser.parse(cfg1, resp.body)
        if (parsed.error != null) {
            val m = "Step 1: HTTP ${resp.code} tetapi parse gagal - ${parsed.error}"
            log(m); _state.update { it.copy(listStatus = m) }
            return
        }
        log("Step 1: HTTP ${resp.code} OK, ${parsed.jobs.size} job diterima")
        _state.update { it.copy(jobs = parsed.jobs) }

        // Tapisan Step 1 (jenis servis, tambang, masa)
        val st = _state.value
        val candidates = mutableListOf<JobCard>()
        for (job in parsed.jobs) {
            val d = JobFilterEngine.checkStep1(job, st.step1Filter)
            log("  [${job.id}] servis=${job.serviceType ?: "-"} tambang=${job.fare ?: "-"} masa=${job.time ?: "-"} -> " +
                if (d.pass) "LULUS" else "GAGAL: ${d.reason}")
            if (d.pass) candidates.add(job)
        }
        log("Tapisan Step 1: ${candidates.size}/${parsed.jobs.size} lulus")

        // Step 2 hanya untuk yang lulus Step 1 dan belum pernah disemak
        val todo = candidates.filter { st.detailMap[it.id] == null }.take(MAX_DETAIL_PER_CYCLE)
        val cfg2 = st.configs[StepConfig.STEP2]
        if (todo.isEmpty()) {
            log("Step 2: tiada job baharu untuk disemak")
        } else if (cfg2 == null || !cfg2.isUsable) {
            log("Step 2: ${StepConfig.STEP2} belum dikonfigurasi/disabled; ${todo.size} job tak disemak")
        } else {
            for (job in todo) {
                val r = api.execute(cfg2, mapOf("jobId" to job.id, "jobType" to (job.jobType ?: "")))
                if (r.error != null || !r.isHttpSuccess) {
                    val why = r.error ?: "HTTP ${r.code}"
                    log("Step 2 [${job.id}]: GAGAL - $why")
                    _state.update { it.copy(step2Errors = it.step2Errors + (job.id to why)) }
                    continue
                }
                val p = JobParser.parseDetail(cfg2, r.body)
                val detail = p.detail
                if (detail == null) {
                    log("Step 2 [${job.id}]: HTTP ${r.code} tetapi parse gagal - ${p.error}")
                    _state.update { it.copy(step2Errors = it.step2Errors + (job.id to (p.error ?: "parse gagal"))) }
                    continue
                }
                _state.update {
                    it.copy(detailMap = it.detailMap + (job.id to detail), step2Errors = it.step2Errors - job.id)
                }
                val dec = JobFilterEngine.checkCriteria(detail, _state.value.criteria)
                log("Step 2 [${job.id}]: HTTP ${r.code} OK pickup=${detail.pickup ?: "-"} " +
                    "dropoff=${detail.dropoff ?: "-"} km=${detail.distanceKm ?: "-"} -> " +
                    if (dec.pass) "SESUAI" else "TAK SESUAI: ${dec.reason}")
            }
        }

        val fin = _state.value
        val sesuai = parsed.jobs.count { fin.evalOf(it).status == JobStatus.SESUAI }
        val m = "Pusingan siap: ${parsed.jobs.size} job, ${candidates.size} lulus Step 1, $sesuai sesuai."
        log(m)
        _state.update { it.copy(listStatus = m) }
    }

    fun setAutoRefresh(on: Boolean) {
        autoJob?.cancel()
        autoJob = null
        _state.update { it.copy(autoRefresh = on) }
        log(if (on) "Auto-refresh ON (${_state.value.intervalSec}s)" else "Auto-refresh OFF")
        if (on) {
            autoJob = viewModelScope.launch {
                while (isActive) {
                    runCycle()
                    delay(_state.value.intervalSec.coerceAtLeast(MIN_INTERVAL_SEC) * 1000L)
                }
            }
        }
    }

    fun setIntervalSec(sec: Int) = _state.update { it.copy(intervalSec = sec.coerceAtLeast(MIN_INTERVAL_SEC)) }

    // ---- Pilihan job ------------------------------------------------------------------------
    fun select(job: JobCard?) {
        _state.update { it.copy(selected = job, accept = ActionState(), confirm = ActionState()) }
    }

    /** Dipanggil jika app dibuka dengan deeplink yang membawa jobId (dan jobType jika ada). */
    fun openFromDeepLink(jobId: String, jobType: String?) =
        select(JobCard(id = jobId, jobType = jobType, title = "Dari deeplink"))

    // ---- Step 3 & 4: tidak diubah. HANYA dipanggil daripada tekanan butang pengguna ----------
    fun userPressedAccept() {
        val job = _state.value.selected ?: return
        val cfg = _state.value.configs[StepConfig.STEP3]
        if (cfg == null || !cfg.isUsable) {
            _state.update { it.copy(accept = ActionState(status = "${StepConfig.STEP3} belum dikonfigurasi/disabled.")) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(accept = ActionState(running = true, status = "Menghantar Accept...")) }
            val resp = api.execute(cfg, vars(job))
            _state.update { it.copy(accept = toAction(resp)) }
        }
    }

    fun userPressedConfirm() {
        val s = _state.value
        val job = s.selected ?: return
        val cfg = s.configs[StepConfig.STEP4]
        if (cfg == null || !cfg.isUsable) {
            _state.update { it.copy(confirm = ActionState(status = "${StepConfig.STEP4} disabled (lalai).")) }
            return
        }
        if (s.accept.httpOk != true) {
            _state.update { it.copy(confirm = ActionState(status = "Accept belum berjaya (HTTP).")) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(confirm = ActionState(running = true, status = "Menghantar Confirm...")) }
            val resp = api.execute(cfg, vars(job))
            _state.update { it.copy(confirm = toAction(resp)) }
        }
    }

    private fun vars(job: JobCard): Map<String, String> =
        mapOf("jobId" to job.id, "jobType" to (job.jobType ?: ""))

    private fun toAction(r: ApiResponse) = ActionState(
        running = false,
        status = r.error ?: "HTTP ${r.code}",
        body = r.body,
        httpOk = r.error == null && r.isHttpSuccess,
    )

    companion object {
        const val MIN_INTERVAL_SEC = 5
        const val MAX_LOG = 300
        const val MAX_DETAIL_PER_CYCLE = 20
    }
}
