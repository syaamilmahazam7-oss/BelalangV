package my.belalang.booking.data

enum class ServiceType(val label: String) {
    KLIA("Tambang ke KLIA"),
    NORMAL("Normal"),
}

/** Satu dokumen dalam koleksi Firestore `api_configs`. */
data class StepConfig(
    val id: String,
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val body: String?,
    val enabled: Boolean,
    val timeoutMs: Long,
    /** Hanya step1: laluan JSON ke array senarai job, contoh `data.bookings`. */
    val listPath: String?,
    /** Hanya step1: kunci kad -> laluan JSON dalam setiap item. */
    val fieldMap: Map<String, String>,
    /** Hanya step1: nilai placeholder `{serviceType}` bagi setiap jenis. */
    val serviceTypeValues: Map<String, String>,
    /** Hanya step1: kata kunci untuk mengenal job KLIA di sisi app. Kosong = tiada tapisan. */
    val kliaKeywords: List<String>,
) {
    val isUsable: Boolean get() = enabled && url.isNotBlank()

    companion object {
        const val STEP1 = "step1_job_list"
        const val STEP2 = "step2_job_details"
        const val STEP3 = "step3_confirmation"
        const val STEP4 = "step4_future"
        val ALL_IDS = listOf(STEP1, STEP2, STEP3, STEP4)

        private fun stringMap(v: Any?): Map<String, String> =
            (v as? Map<*, *>)?.entries
                ?.filter { it.key != null && it.value != null }
                ?.associate { it.key.toString() to it.value.toString() }
                ?: emptyMap()

        fun fromMap(id: String, data: Map<String, Any?>): StepConfig {
            val method = (data["method"] as? String)?.trim()?.uppercase().orEmpty().ifEmpty { "GET" }
            return StepConfig(
                id = id,
                url = (data["url"] as? String)?.trim().orEmpty(),
                method = method,
                headers = stringMap(data["headers"]),
                body = (data["body"] as? String)?.takeIf { it.isNotBlank() },
                // Step 4 mesti disabled secara lalai.
                enabled = (data["enabled"] as? Boolean) ?: (id != STEP4),
                timeoutMs = ((data["timeoutMs"] as? Number)?.toLong() ?: 15_000L).coerceIn(1_000L, 120_000L),
                listPath = (data["listPath"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                fieldMap = stringMap(data["fieldMap"]),
                serviceTypeValues = stringMap(data["serviceTypeValues"]),
                kliaKeywords = (data["kliaKeywords"] as? List<*>)
                    ?.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
                    ?: emptyList(),
            )
        }
    }
}

data class JobCard(
    val id: String,
    val jobType: String? = null,
    val title: String? = null,
    val pickup: String? = null,
    val dropoff: String? = null,
    val distanceKm: Double? = null,
    val fare: String? = null,
    val time: String? = null,
    val serviceType: String? = null,
) {
    /** Semua teks untuk carian. */
    val searchText: String
        get() = listOfNotNull(id, jobType, title, pickup, dropoff, fare, time, serviceType).joinToString(" ")
}

data class ApiResponse(val code: Int?, val body: String, val error: String?) {
    val isHttpSuccess: Boolean get() = code != null && code in 200..299
}
