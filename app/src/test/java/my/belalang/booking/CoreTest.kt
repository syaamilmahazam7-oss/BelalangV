package my.belalang.booking

import my.belalang.booking.data.JobCard
import my.belalang.booking.data.JobParser
import my.belalang.booking.data.JsonPath
import my.belalang.booking.data.ServiceType
import my.belalang.booking.data.StepConfig
import my.belalang.booking.data.Template
import my.belalang.booking.domain.JobFilter
import my.belalang.booking.domain.JobFilterEngine
import my.belalang.booking.domain.SortKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreTest {
    // Data contoh rekaan untuk ujian sahaja; BUKAN format API sebenar.
    private val sample = """{"payload":{"items":[
        {"ref":"A1","kind":"x","from":"Taman A","to":"KLIA T1","dist":"12.5 Km","price":"RM80.00","at":"06:00"},
        {"ref":"A2","kind":"x","from":"Taman B","to":"Bandar C","dist":"30","price":"RM40.00","at":"07:00"},
        {"ref":"A3","kind":"y","from":"Pantai","to":"KLIA T2","dist":"45.1","price":"RM120.00","at":"05:00"}
    ]}}"""

    private fun cfg(map: Map<String, Any?>) = StepConfig.fromMap(StepConfig.STEP1, map)

    private val step1 = cfg(
        mapOf(
            "url" to "https://example.invalid/list",
            "listPath" to "payload.items",
            "fieldMap" to mapOf(
                "id" to "ref", "jobType" to "kind", "pickup" to "from", "dropoff" to "to",
                "distanceKm" to "dist", "fare" to "price", "time" to "at",
            ),
            "kliaKeywords" to listOf("KLIA"),
        ),
    )

    @Test fun jsonPath_resolvesNestedAndIndex() {
        val root = JsonPath.parseRoot("""{"a":{"b":[{"c":5},{"c":6}]}}""")
        assertEquals(6, JsonPath.resolve(root, "a.b[1].c"))
        assertEquals(5, JsonPath.resolve(root, "$.a.b.0.c"))
        assertNull(JsonPath.resolve(root, "a.x"))
        assertNull(JsonPath.resolve(root, "a.b[9]"))
    }

    @Test fun parser_buildsCards() {
        val r = JobParser.parse(step1, sample)
        assertNull(r.error)
        assertEquals(3, r.jobs.size)
        assertEquals(12.5, r.jobs[0].distanceKm!!, 0.0001)
        assertEquals("KLIA T1", r.jobs[0].dropoff)
    }

    @Test fun parser_reportsMissingConfigInsteadOfGuessing() {
        val noMap = cfg(mapOf("url" to "https://example.invalid/list"))
        assertNotNull(JobParser.parse(noMap, sample).error)
        val badPath = cfg(mapOf("fieldMap" to mapOf("id" to "ref"), "listPath" to "nope"))
        assertNotNull(JobParser.parse(badPath, sample).error)
        assertNotNull(JobParser.parse(step1, "bukan json").error)
    }

    @Test fun step4_disabledByDefault() {
        assertFalse(StepConfig.fromMap(StepConfig.STEP4, mapOf("url" to "https://example.invalid")).enabled)
        assertTrue(StepConfig.fromMap(StepConfig.STEP3, mapOf("url" to "https://example.invalid")).enabled)
        assertTrue(StepConfig.fromMap(StepConfig.STEP4, mapOf("enabled" to true)).enabled)
    }

    @Test fun filter_serviceTypeByConfiguredKeywords() {
        val jobs = JobParser.parse(step1, sample).jobs
        val klia = JobFilterEngine.apply(jobs, JobFilter(ServiceType.KLIA, listOf("KLIA")))
        assertEquals(setOf("A1", "A3"), klia.map { it.id }.toSet())
        val normal = JobFilterEngine.apply(jobs, JobFilter(ServiceType.NORMAL, listOf("KLIA")))
        assertEquals(listOf("A2"), normal.map { it.id })
        // Tanpa kata kunci dikonfigurasi: tiada tapisan jenis servis.
        assertEquals(3, JobFilterEngine.apply(jobs, JobFilter(ServiceType.KLIA, emptyList())).size)
    }

    @Test fun filter_distanceLocationAvoidSearchSort() {
        val jobs = JobParser.parse(step1, sample).jobs
        val base = JobFilter(ServiceType.KLIA, emptyList())
        assertEquals(listOf("A1", "A2"), JobFilterEngine.apply(jobs, base.copy(maxKm = 35.0)).map { it.id })
        assertEquals(listOf("A2", "A3"), JobFilterEngine.apply(jobs, base.copy(avoidLocations = listOf("taman a"))).map { it.id })
        assertEquals(listOf("A3"), JobFilterEngine.apply(jobs, base.copy(includeLocations = listOf("pantai"))).map { it.id })
        assertEquals(listOf("A2"), JobFilterEngine.apply(jobs, base.copy(query = "bandar")).map { it.id })
        assertEquals(listOf("A3", "A1", "A2"),
            JobFilterEngine.apply(jobs, base.copy(sortKey = SortKey.FARE, ascending = false)).map { it.id })
        assertEquals(listOf("A3", "A1", "A2"),
            JobFilterEngine.apply(jobs, base.copy(sortKey = SortKey.TIME)).map { it.id })
    }

    @Test fun filter_unknownDistanceIsKept() {
        val j = listOf(JobCard(id = "Z", distanceKm = null))
        assertEquals(1, JobFilterEngine.apply(j, JobFilter(maxKm = 1.0)).size)
    }

    @Test fun template_replacesAndFlagsUnresolved() {
        assertEquals("/job/a%20b", Template.apply("/job/{jobId}", mapOf("jobId" to "a b"), urlEncode = true))
        assertEquals("""{"id":"7"}""", Template.apply("""{"id":"{jobId}"}""", mapOf("jobId" to "7")))
        assertEquals(listOf("{jobType}"), Template.unresolved("x/{jobType}"))
        assertTrue(Template.unresolved("""{"a":1}""").isEmpty())
    }
}
