package my.belalang.booking.domain

import my.belalang.booking.data.JobCard
import my.belalang.booking.data.JobParser
import my.belalang.booking.data.ServiceType

enum class SortKey(val label: String) {
    DISTANCE("Jarak"),
    FARE("Tambang"),
    TIME("Masa"),
}

data class JobFilter(
    val serviceType: ServiceType = ServiceType.NORMAL,
    val kliaKeywords: List<String> = emptyList(),
    val query: String = "",
    /** Lokasi yang dikehendaki (pickup atau dropoff mengandungi salah satu). Kosong = semua. */
    val includeLocations: List<String> = emptyList(),
    /** Lokasi yang perlu dielakkan. */
    val avoidLocations: List<String> = emptyList(),
    val maxKm: Double? = null,
    val sortKey: SortKey = SortKey.DISTANCE,
    val ascending: Boolean = true,
)

object JobFilterEngine {
    fun splitList(raw: String): List<String> =
        raw.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    private fun String?.has(any: List<String>): Boolean =
        this != null && any.any { this.contains(it, ignoreCase = true) }

    private fun isKlia(job: JobCard, keywords: List<String>): Boolean =
        listOfNotNull(job.title, job.dropoff, job.serviceType).joinToString(" ").has(keywords)

    fun matches(job: JobCard, f: JobFilter): Boolean {
        // Tapisan jenis servis hanya jika kata kunci KLIA dikonfigurasi dari Firestore.
        if (f.kliaKeywords.isNotEmpty()) {
            val klia = isKlia(job, f.kliaKeywords)
            if (f.serviceType == ServiceType.KLIA && !klia) return false
            if (f.serviceType == ServiceType.NORMAL && klia) return false
        }
        if (f.query.isNotBlank() && !job.searchText.contains(f.query.trim(), ignoreCase = true)) return false
        if (f.includeLocations.isNotEmpty() &&
            !(job.pickup.has(f.includeLocations) || job.dropoff.has(f.includeLocations))
        ) return false
        if (job.pickup.has(f.avoidLocations) || job.dropoff.has(f.avoidLocations)) return false
        val km = job.distanceKm
        if (f.maxKm != null && km != null && km > f.maxKm) return false
        return true
    }

    fun apply(jobs: List<JobCard>, f: JobFilter): List<JobCard> {
        val kept = jobs.filter { matches(it, f) }
        val sorted = when (f.sortKey) {
            SortKey.DISTANCE -> kept.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
            SortKey.FARE -> kept.sortedBy { JobParser.extractNumber(it.fare) ?: Double.MAX_VALUE }
            SortKey.TIME -> kept.sortedBy { it.time ?: "￿" }
        }
        return if (f.ascending) sorted else sorted.reversed()
    }
}
