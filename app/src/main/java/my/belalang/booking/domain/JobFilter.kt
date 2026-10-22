package my.belalang.booking.domain

import my.belalang.booking.data.JobCard
import my.belalang.booking.data.JobDetail
import my.belalang.booking.data.JobParser
import my.belalang.booking.data.ServiceType

enum class SortKey(val label: String) {
    DISTANCE("Jarak"),
    FARE("Tambang"),
    TIME("Masa"),
}

/** Tapisan Step 1: jenis servis, tambang, masa (data dari senarai). */
data class Step1Filter(
    val serviceType: ServiceType = ServiceType.NORMAL,
    /** Kata kunci KLIA dari Firestore; kosong = tiada tapisan jenis servis. */
    val kliaKeywords: List<String> = emptyList(),
    val fareMin: Double? = null,
    val fareMax: Double? = null,
    /** Jam pickup yang dibenarkan (0-23). Kosong = semua jam. */
    val hours: Set<Int> = emptySet(),
)

/** Criteria Step 2: lokasi dan jarak (data dari butiran booking). */
data class Criteria(
    val includeLocations: List<String> = emptyList(),
    val avoidLocations: List<String> = emptyList(),
    val maxKm: Double? = null,
)

data class Decision(val pass: Boolean, val reason: String)

object JobFilterEngine {
    private val TIME = Regex("(\\d{1,2}):(\\d{2})\\s*([AaPp][Mm])?")

    fun splitList(raw: String): List<String> =
        raw.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    /** Jam 0-23 dari teks masa seperti `06:30 AM`, `18:05` atau `2026-10-22 06:30`. */
    fun parseHour(time: String?): Int? {
        val m = time?.let { TIME.find(it) } ?: return null
        var h = m.groupValues[1].toIntOrNull() ?: return null
        val ampm = m.groupValues[3].uppercase()
        if (ampm == "PM" && h < 12) h += 12
        if (ampm == "AM" && h == 12) h = 0
        return if (h in 0..23) h else null
    }

    private fun String?.hasAny(list: List<String>): String? =
        if (this == null) null else list.firstOrNull { this.contains(it, ignoreCase = true) }

    private fun isKlia(job: JobCard, keywords: List<String>): Boolean =
        listOfNotNull(job.serviceType, job.title, job.dropoff).joinToString(" ").hasAny(keywords) != null

    fun checkStep1(job: JobCard, f: Step1Filter): Decision {
        if (f.kliaKeywords.isNotEmpty()) {
            val klia = isKlia(job, f.kliaKeywords)
            if (f.serviceType == ServiceType.KLIA && !klia) return Decision(false, "bukan servis KLIA")
            if (f.serviceType == ServiceType.NORMAL && klia) return Decision(false, "servis KLIA (dipilih Normal)")
        }
        if (f.fareMin != null || f.fareMax != null) {
            val fare = JobParser.extractNumber(job.fare)
                ?: return Decision(false, "tambang tak dapat dibaca (${job.fare})")
            if (f.fareMin != null && fare < f.fareMin) return Decision(false, "tambang $fare < min ${f.fareMin}")
            if (f.fareMax != null && fare > f.fareMax) return Decision(false, "tambang $fare > maks ${f.fareMax}")
        }
        if (f.hours.isNotEmpty()) {
            val h = parseHour(job.time) ?: return Decision(false, "masa tak dapat dibaca (${job.time})")
            if (h !in f.hours) return Decision(false, "jam $h tidak dipilih")
        }
        return Decision(true, "lulus")
    }

    fun checkCriteria(d: JobDetail, c: Criteria): Decision {
        c.avoidLocations.let { avoid ->
            val hit = d.pickup.hasAny(avoid) ?: d.dropoff.hasAny(avoid)
            if (hit != null) return Decision(false, "lokasi dielak: $hit")
        }
        if (c.includeLocations.isNotEmpty()) {
            val ok = d.pickup.hasAny(c.includeLocations) != null || d.dropoff.hasAny(c.includeLocations) != null
            if (!ok) return Decision(false, "lokasi tiada dalam senarai dikehendaki")
        }
        if (c.maxKm != null) {
            val km = d.distanceKm ?: return Decision(false, "km tiada dalam respons")
            if (km > c.maxKm) return Decision(false, "km $km > maks ${c.maxKm}")
        }
        return Decision(true, "sesuai")
    }
}
