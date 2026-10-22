package my.belalang.booking.data

import org.json.JSONArray
import org.json.JSONObject

data class ParseResult(val jobs: List<JobCard>, val error: String?)

data class DetailResult(val detail: JobDetail?, val error: String?)

/**
 * Menukar respons senarai job kepada kad berdasarkan `listPath` dan `fieldMap` dari Firestore.
 * TIADA andaian tentang format respons: jika konfigurasi tiada, ralat dilaporkan.
 */
object JobParser {
    private val NUMBER = Regex("[-+]?[0-9]*\\.?[0-9]+")

    /** Hanya nama kunci aras atas (bukan nilai) untuk bantu nyahpepijat. */
    fun describe(root: Any?): String = when (root) {
        is JSONObject -> "objek, kunci: " + root.keys().asSequence().take(12).joinToString(", ").ifEmpty { "(kosong)" }
        is JSONArray -> "array, ${root.length()} item"
        else -> "tidak diketahui"
    }

    private val TAGS = Regex("<[^>]*>")

    /** Buang tag HTML ringkas (contoh `<b>05:30 PM</b>`) supaya teks bersih. */
    fun clean(s: String?): String? = s?.let { if (it.contains('<')) TAGS.replace(it, "").trim() else it }

    fun extractNumber(s: String?): Double? = s?.let { NUMBER.find(it)?.value?.toDoubleOrNull() }

    fun parse(cfg: StepConfig, body: String): ParseResult {
        val root = JsonPath.parseRoot(body)
            ?: return ParseResult(emptyList(), "Respons bukan JSON object/array (${JsonPath.nonJsonHint(body)}).")
        if (cfg.fieldMap["id"].isNullOrBlank()) {
            return ParseResult(emptyList(), "fieldMap.id belum diset dalam ${cfg.id}.")
        }
        val list = JsonPath.resolve(root, cfg.listPath) as? JSONArray
            ?: return ParseResult(
                emptyList(),
                "listPath '${cfg.listPath ?: "$"}' tidak menghala ke array dalam respons. Bentuk respons: ${describe(root)}",
            )

        val jobs = mutableListOf<JobCard>()
        for (i in 0 until list.length()) {
            val item: JSONObject = list.optJSONObject(i) ?: continue
            fun field(key: String): String? =
                cfg.fieldMap[key]?.let { clean(JsonPath.asString(JsonPath.resolve(item, it))) }
            val id = field("id")?.takeIf { it.isNotBlank() } ?: continue
            jobs.add(
                JobCard(
                    id = id,
                    jobType = field("jobType"),
                    title = field("title"),
                    pickup = field("pickup"),
                    dropoff = field("dropoff"),
                    distanceKm = extractNumber(field("distanceKm")),
                    fare = field("fare"),
                    time = field("time"),
                    serviceType = field("serviceType"),
                ),
            )
        }
        return ParseResult(jobs, null)
    }

    /** Step 2: baca pickup, dropoff dan km ikut `fieldMap` (+ `detailPath` pilihan) dalam step2_job_details. */
    fun parseDetail(cfg: StepConfig, body: String): DetailResult {
        val root = JsonPath.parseRoot(body)
            ?: return DetailResult(null, "Respons bukan JSON object/array (${JsonPath.nonJsonHint(body)}).")
        if (cfg.fieldMap.isEmpty()) {
            return DetailResult(null, "fieldMap belum diset dalam ${cfg.id}.")
        }
        val node = JsonPath.resolve(root, cfg.detailPath)
            ?: return DetailResult(null, "detailPath '${cfg.detailPath ?: "$"}' tiada dalam respons.")
        fun field(key: String): String? =
            cfg.fieldMap[key]?.let { JsonPath.asString(JsonPath.resolve(node, it)) }
        return DetailResult(
            JobDetail(
                pickup = field("pickup"),
                dropoff = field("dropoff"),
                distanceKm = extractNumber(field("distanceKm")),
            ),
            null,
        )
    }
}
