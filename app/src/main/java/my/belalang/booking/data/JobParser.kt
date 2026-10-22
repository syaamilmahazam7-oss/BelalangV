package my.belalang.booking.data

import org.json.JSONArray
import org.json.JSONObject

data class ParseResult(val jobs: List<JobCard>, val error: String?)

/**
 * Menukar respons senarai job kepada kad berdasarkan `listPath` dan `fieldMap` dari Firestore.
 * TIADA andaian tentang format respons: jika konfigurasi tiada, ralat dilaporkan.
 */
object JobParser {
    private val NUMBER = Regex("[-+]?[0-9]*\\.?[0-9]+")

    fun extractNumber(s: String?): Double? = s?.let { NUMBER.find(it)?.value?.toDoubleOrNull() }

    fun parse(cfg: StepConfig, body: String): ParseResult {
        val root = JsonPath.parseRoot(body)
            ?: return ParseResult(emptyList(), "Respons bukan JSON object/array.")
        if (cfg.fieldMap["id"].isNullOrBlank()) {
            return ParseResult(emptyList(), "fieldMap.id belum diset dalam ${cfg.id}.")
        }
        val list = JsonPath.resolve(root, cfg.listPath) as? JSONArray
            ?: return ParseResult(emptyList(), "listPath '${cfg.listPath ?: "$"}' tidak menghala ke array dalam respons.")

        val jobs = mutableListOf<JobCard>()
        for (i in 0 until list.length()) {
            val item: JSONObject = list.optJSONObject(i) ?: continue
            fun field(key: String): String? =
                cfg.fieldMap[key]?.let { JsonPath.asString(JsonPath.resolve(item, it)) }
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
}
