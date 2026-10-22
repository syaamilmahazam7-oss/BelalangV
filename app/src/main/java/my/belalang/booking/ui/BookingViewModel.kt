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
import my.belalang.booking.data.ConfigRepository
import my.belalang.booking.data.JobCard
import my.belalang.booking.data.JobParser
import my.belalang.booking.data.ServiceType
import my.belalang.booking.data.StepConfig
import my.belalang.booking.domain.JobFilter
import my.belalang.booking.domain.JobFilterEngine
import my.belalang.booking.domain.SortKey

data class ActionState(
    val running: Boolean = false,
    val status: String = "",
    val body: String = "",
    /** null = belum cuba; true = HTTP 2xx diterima; false = gagal. Ini BUKAN bukti hasil sebenar. */
    val httpOk: Boolean? = null,
)

data class UiState(
    val configs: Map<String, StepConfig> = emptyMap(),
    val configError: String? = null,
    val configLoading: Boolean = false,

    val jobs: List<JobCard> = emptyList(),
    val listStatus: String = "Belum dimuat.",
    val listLoading: Boolean = false,

    val serviceType: ServiceType = ServiceType.NORMAL,
    val query: String = "",
    val includeLocations: String = "",
    val avoidLocations: String = "",
    val maxKm: String = "",
    val sortKey: SortKey = SortKey.DISTANCE,
    val ascending: Boolean = true,

    val autoRefresh: Boolean = false,
    val intervalSec: Int = 30,

    val selected: JobCard? = null,
    val details: ActionState = ActionState(),
    val accept: ActionState = ActionState(),
    val confirm: ActionState = ActionState(),
) {
    val filter: JobFilter
        get() = JobFilter(
            serviceType = serviceType,
            kliaKeywords = configs[StepConfig.STEP1]?.kliaKeywords ?: emptyList(),
            query = query,
            includeLocations = JobFilterEngine.splitList(includeLocations),
            avoidLocations = JobFilterEngine.splitList(avoidLocations),
            maxKm = maxKm.trim().toDoubleOrNull(),
            sortKey = sortKey,
            ascending = ascending,
        )

    val visibleJobs: List<JobCard> get() = JobFilterEngine.apply(jobs, filter)
}

class BookingViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ConfigRepository(app)
    private val api = ApiClient()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private var autoJob: Job? = null

    init {
        loadConfigs()
    }

    // ---- Konfigurasi -------------------------------------------------------------------------
    fun loadConfigs() {
        viewModelScope.launch {
            _state.update { it.copy(configLoading = true) }
            val r = repo.load()
            _state.update { it.copy(configs = r.configs, configError = r.error, configLoading = false) }
        }
    }

    // ---- Penapis / sorting (hanya UI, tiada rangkaian) --------------------------------------
    fun setServiceType(t: ServiceType) = _state.update { it.copy(serviceType = t) }
    fun setQuery(v: String) = _state.update { it.copy(query = v) }
    fun setInclude(v: String) = _state.update { it.copy(includeLocations = v) }
    fun setAvoid(v: String) = _state.update { it.copy(avoidLocations = v) }
    fun setMaxKm(v: String) = _state.update { it.copy(maxKm = v) }
    fun setSort(k: SortKey) = _state.update {
        if (it.sortKey == k) it.copy(ascending = !it.ascending) else it.copy(sortKey = k, ascending = true)
    }

    // ---- Senarai job (baca sahaja) -----------------------------------------------------------
    fun refreshJobs() {
        viewModelScope.launch { doRefresh() }
    }

    private suspend fun doRefresh() {
        val cfg = _state.value.configs[StepConfig.STEP1]
        when {
            cfg == null -> {
                _state.update { it.copy(listStatus = "Dokumen ${StepConfig.STEP1} tiada dalam Firestore.") }
                return
            }
            !cfg.isUsable -> {
                _state.update { it.copy(listStatus = "${StepConfig.STEP1} disabled atau URL kosong.") }
                return
            }
        }
        _state.update { it.copy(listLoading = true) }
        val typeValue = _state.value.configs[StepConfig.STEP1]?.serviceTypeValues
            ?.get(_state.value.serviceType.name.lowercase()).orEmpty()
        val resp = api.execute(cfg!!, mapOf("serviceType" to typeValue))
        if (resp.error != null || !resp.isHttpSuccess) {
            _state.update {
                it.copy(listLoading = false, listStatus = resp.error ?: "HTTP ${resp.code}: ${resp.body.take(200)}")
            }
            return
        }
        val parsed = JobParser.parse(cfg, resp.body)
        _state.update {
            it.copy(
                listLoading = false,
                jobs = parsed.jobs,
                listStatus = parsed.error ?: "${parsed.jobs.size} job diterima (HTTP ${resp.code}).",
            )
        }
    }

    fun setAutoRefresh(on: Boolean) {
        autoJob?.cancel()
        autoJob = null
        _state.update { it.copy(autoRefresh = on) }
        if (on) {
            autoJob = viewModelScope.launch {
                while (isActive) {
                    doRefresh()
                    delay(_state.value.intervalSec.coerceAtLeast(MIN_INTERVAL_SEC) * 1000L)
                }
            }
        }
    }

    fun setIntervalSec(sec: Int) = _state.update { it.copy(intervalSec = sec.coerceAtLeast(MIN_INTERVAL_SEC)) }

    // ---- Butiran job (Step 2, baca sahaja) ---------------------------------------------------
    fun select(job: JobCard?) {
        _state.update {
            it.copy(selected = job, details = ActionState(), accept = ActionState(), confirm = ActionState())
        }
        if (job != null) fetchDetails(job)
    }

    /** Dipanggil jika app dibuka dengan deeplink yang membawa jobId (dan jobType jika ada). */
    fun openFromDeepLink(jobId: String, jobType: String?) =
        select(JobCard(id = jobId, jobType = jobType, title = "Dari deeplink"))

    private fun fetchDetails(job: JobCard) {
        val cfg = _state.value.configs[StepConfig.STEP2]
        if (cfg == null || !cfg.isUsable) {
            _state.update { it.copy(details = ActionState(status = "${StepConfig.STEP2} belum dikonfigurasi/disabled.")) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(details = ActionState(running = true, status = "Memuatkan butiran...")) }
            val resp = api.execute(cfg, vars(job))
            _state.update { it.copy(details = toAction(resp)) }
        }
    }

    // ---- Step 3 & 4: HANYA dipanggil daripada tekanan butang pengguna -----------------------
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

    private fun toAction(r: my.belalang.booking.data.ApiResponse) = ActionState(
        running = false,
        status = r.error ?: "HTTP ${r.code}",
        body = r.body,
        httpOk = r.error == null && r.isHttpSuccess,
    )

    companion object {
        const val MIN_INTERVAL_SEC = 5
    }
}
