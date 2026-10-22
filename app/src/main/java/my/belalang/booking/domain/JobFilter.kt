package my.belalang.booking.domain

import my.belalang.booking.data.JobCard
import my.belalang.booking.data.JobDetail
import my.belalang.booking.data.JobParser

enum class SortKey(val label: String) {
    DISTANCE("Jarak"),
    FARE("Tambang"),
    TIME("Masa"),
}

/** Kelas servis (modul A). Dipadankan pada teks servis/tajuk job, tidak peka huruf besar/kecil. */
enum class ServiceClass(val label: String, val pattern: Regex) {
    STANDARD("STANDARD", Regex("standard", RegexOption.IGNORE_CASE)),
    PLUS("PLUS", Regex("\\bplus\\b", RegexOption.IGNORE_CASE)),
    SEATS6("6 SEATS", Regex("6\\s*seat", RegexOption.IGNORE_CASE)),
    PREMIUM("PREMIUM", Regex("premium", RegexOption.IGNORE_CASE)),
    EXECUTIVE("EXECUTIVE", Regex("executive", RegexOption.IGNORE_CASE)),
    PREMIUM_EV("PREMIUM | EV", Regex("premium\\s*\\|?\\s*ev", RegexOption.IGNORE_CASE)),
    EV_6_SEATS("EV 6 SEATS", Regex("ev\\s*6\\s*seat", RegexOption.IGNORE_CASE)),
}

enum class JobCategory { TO_KLIA, FROM_KLIA, NORMAL }

/** Tapisan Step 1 (data dari senarai): servis, kategori KLIA/normal + harga minimum, masa, kawasan. */
data class Step1Filter(
    /** Kosong = semua servis. */
    val serviceClasses: Set<ServiceClass> = emptySet(),
    val toKlia: Boolean = false,
    val fromKlia: Boolean = false,
    val normalOn: Boolean = false,
    val minFareKlia: Double? = null,
    val minFareNormal: Double? = null,
    /** Kata kunci KLIA; kosong = semua job dianggap NORMAL. */
    val kliaKeywords: List<String> = emptyList(),
    /** true = Semua Masa. */
    val timeAll: Boolean = true,
    /** Jam dibenarkan (0-23) bila timeAll = false. */
    val hours: Set<Int> = emptySet(),
    val areaOn: Boolean = false,
    /** true = ELAK kawasan ini, false = HANYA kawasan ini. */
    val areaAvoid: Boolean = true,
    val areaKeywords: List<String> = emptyList(),
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

    /** FROM_KLIA jika pickup mengandungi kata kunci; TO_KLIA jika servis/tajuk/drop-off; selain itu NORMAL. */
    fun categoryOf(job: JobCard, keywords: List<String>): JobCategory {
        if (keywords.isEmpty()) return JobCategory.NORMAL
        if (job.pickup.hasAny(keywords) != null) return JobCategory.FROM_KLIA
        val text = listOfNotNull(job.serviceType, job.title, job.dropoff).joinToString(" ")
        return if (text.hasAny(keywords) != null) JobCategory.TO_KLIA else JobCategory.NORMAL
    }

    fun checkStep1(job: JobCard, f: Step1Filter): Decision {
        // A. Servis
        if (f.serviceClasses.isNotEmpty()) {
            val text = listOfNotNull(job.serviceType, job.title).joinToString(" ")
            if (f.serviceClasses.none { it.pattern.containsMatchIn(text) }) {
                return Decision(false, "servis tidak dipilih (${job.serviceType ?: "-"})")
            }
        }
        // B. Kategori KLIA / normal dan harga minimum
        if (f.toKlia || f.fromKlia || f.normalOn) {
            val cat = categoryOf(job, f.kliaKeywords)
            val allowed = when (cat) {
                JobCategory.TO_KLIA -> f.toKlia
                JobCategory.FROM_KLIA -> f.fromKlia
                JobCategory.NORMAL -> f.normalOn
            }
            if (!allowed) return Decision(false, "kategori ${cat.name} tidak dipilih")
            val min = if (cat == JobCategory.NORMAL) f.minFareNormal else f.minFareKlia
            if (min != null) {
                val fare = JobParser.extractNumber(job.fare)
                    ?: return Decision(false, "tambang tak dapat dibaca (${job.fare})")
                if (fare < min) return Decision(false, "tambang $fare < min $min (${cat.name})")
            }
        }
        // C. Masa
        if (!f.timeAll) {
            val h = parseHour(job.time) ?: return Decision(false, "masa tak dapat dibaca (${job.time})")
            if (h !in f.hours) return Decision(false, "jam $h tidak dipilih")
        }
        // E. Kawasan (hanya jika pickup/drop-off sudah ada dalam data Step 1)
        if (f.areaOn && f.areaKeywords.isNotEmpty() && (job.pickup != null || job.dropoff != null)) {
            val hit = job.pickup.hasAny(f.areaKeywords) ?: job.dropoff.hasAny(f.areaKeywords)
            if (f.areaAvoid && hit != null) return Decision(false, "kawasan dielak: $hit")
            if (!f.areaAvoid && hit == null) return Decision(false, "bukan dalam kawasan HANYA")
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
