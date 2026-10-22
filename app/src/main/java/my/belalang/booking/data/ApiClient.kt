package my.belalang.booking.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object Template {
    private val PLACEHOLDER = Regex("\\{[A-Za-z0-9_]+\\}")

    fun apply(s: String, vars: Map<String, String>, urlEncode: Boolean = false): String {
        var r = s
        for ((k, v) in vars) {
            val value = if (urlEncode) URLEncoder.encode(v, "UTF-8").replace("+", "%20") else v
            r = r.replace("{$k}", value)
        }
        return r
    }

    fun unresolved(s: String): List<String> = PLACEHOLDER.findAll(s).map { it.value }.distinct().toList()
}

/**
 * Pelaksana request generik. Semua nilai (URL, method, headers, body) datang dari StepConfig.
 * Kelas ini TIDAK memanggil apa-apa sendiri; pemanggil menentukan bila.
 * Header/body tidak pernah dilog.
 */
class ApiClient(private val base: OkHttpClient = OkHttpClient()) {
    private val bodyMethods = setOf("POST", "PUT", "PATCH", "DELETE")
    private val allowedMethods = setOf("GET", "POST", "PUT", "PATCH", "DELETE")

    suspend fun execute(cfg: StepConfig, vars: Map<String, String> = emptyMap()): ApiResponse =
        withContext(Dispatchers.IO) {
            if (cfg.url.isBlank()) return@withContext ApiResponse(null, "", "URL belum diset dalam ${cfg.id}.")
            if (cfg.method !in allowedMethods) {
                return@withContext ApiResponse(null, "", "Method '${cfg.method}' tidak disokong.")
            }
            val url = Template.apply(cfg.url, vars, urlEncode = true)
            val body = cfg.body?.let { Template.apply(it, vars) }
            val headers = cfg.headers.mapValues { Template.apply(it.value, vars) }

            val missing = (Template.unresolved(url) + (body?.let { Template.unresolved(it) } ?: emptyList()) +
                headers.values.flatMap { Template.unresolved(it) }).distinct()
            if (missing.isNotEmpty()) {
                return@withContext ApiResponse(null, "", "Placeholder belum diisi: ${missing.joinToString(", ")}")
            }

            try {
                val client = base.newBuilder().callTimeout(cfg.timeoutMs, TimeUnit.MILLISECONDS).build()
                val contentType = headers.entries.firstOrNull { it.key.equals("Content-Type", true) }?.value
                    ?: "application/json"
                val reqBody = if (cfg.method in bodyMethods) {
                    (body ?: "").toRequestBody(contentType.toMediaTypeOrNull())
                } else null
                val builder = Request.Builder().url(url).method(cfg.method, reqBody)
                headers.forEach { (k, v) -> builder.header(k, v) }
                client.newCall(builder.build()).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    ApiResponse(resp.code, text.take(MAX_BODY), null)
                }
            } catch (e: Exception) {
                ApiResponse(null, "", "Ralat rangkaian: ${e.javaClass.simpleName}")
            }
        }

    companion object {
        const val MAX_BODY = 20_000
    }
}
