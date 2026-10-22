package my.belalang.booking

import my.belalang.booking.data.JobCard
import my.belalang.booking.data.JobDetail
import my.belalang.booking.data.JobParser
import my.belalang.booking.data.JsonPath
import my.belalang.booking.data.StepConfig
import my.belalang.booking.data.Template
import my.belalang.booking.domain.Criteria
import my.belalang.booking.domain.JobCategory
import my.belalang.booking.domain.JobFilterEngine
import my.belalang.booking.domain.ServiceClass
import my.belalang.booking.domain.Step1Filter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreTest {
    // Data contoh rekaan untuk ujian sahaja; BUKAN format API sebenar.
    private val list = """{"payload":{"items":[
        {"ref":"A1","svc":"Standard (To KLIA)","price":"RM80.00","at":"06:00 AM"},
        {"ref":"A2","svc":"Standard","price":"RM40.00","at":"07:30 AM"},
        {"ref":"A3","svc":"Plus (To KLIA)","price":"RM120.00","at":"05:00 PM"}
    ]}}"""

    private val detail = """{"booking":{"from":"Taman A","to":"KLIA T1","km":"12.5 Km"}}"""

    private val step1 = StepConfig.fromMap(
        StepConfig.STEP1,
        mapOf(
            "url" to "https://example.invalid/list",
            "listPath" to "payload.items",
            "fieldMap" to mapOf("id" to "ref", "serviceType" to "svc", "fare" to "price", "time" to "at"),
            "kliaKeywords" to listOf("KLIA"),
        ),
    )

    private val step2 = StepConfig.fromMap(
        StepConfig.STEP2,
        mapOf(
            "url" to "https://example.invalid/booking/{jobId}",
            "detailPath" to "booking",
            "fieldMap" to mapOf("pickup" to "from", "dropoff" to "to", "distanceKm" to "km"),
        ),
    )

    @Test fun jsonPath_resolvesNestedAndIndex() {
        val root = JsonPath.parseRoot("""{"a":{"b":[{"c":5},{"c":6}]}}""")
        assertEquals(6, JsonPath.resolve(root, "a.b[1].c"))
        assertEquals(5, JsonPath.resolve(root, "$.a.b.0.c"))
        assertNull(JsonPath.resolve(root, "a.x"))
        assertNull(JsonPath.resolve(root, "a.b[9]"))
    }

    @Test fun step1Parser_buildsCards() {
        val r = JobParser.parse(step1, list)
        assertNull(r.error)
        assertEquals(3, r.jobs.size)
        assertEquals("Standard (To KLIA)", r.jobs[0].serviceType)
        assertEquals("RM80.00", r.jobs[0].fare)
        assertNull(r.jobs[0].pickup) // pickup datang dari Step 2, bukan Step 1
    }

    @Test fun step2Parser_readsPickupDropoffKm() {
        val r = JobParser.parseDetail(step2, detail)
        assertNull(r.error)
        assertEquals("Taman A", r.detail!!.pickup)
        assertEquals("KLIA T1", r.detail!!.dropoff)
        assertEquals(12.5, r.detail!!.distanceKm!!, 0.0001)
    }

    @Test fun parsers_reportMissingConfigInsteadOfGuessing() {
        assertNotNull(JobParser.parse(StepConfig.fromMap("x", mapOf("url" to "u")), list).error)
        assertNotNull(JobParser.parse(step1, "bukan json").error)
        assertNotNull(JobParser.parseDetail(StepConfig.fromMap("x", mapOf("url" to "u")), detail).error)
        val badPath = StepConfig.fromMap("x", mapOf("fieldMap" to mapOf("pickup" to "from"), "detailPath" to "nope"))
        assertNotNull(JobParser.parseDetail(badPath, detail).error)
    }

    @Test fun step4_disabledByDefault() {
        assertFalse(StepConfig.fromMap(StepConfig.STEP4, mapOf("url" to "https://example.invalid")).enabled)
        assertTrue(StepConfig.fromMap(StepConfig.STEP3, mapOf("url" to "https://example.invalid")).enabled)
        assertTrue(StepConfig.fromMap(StepConfig.STEP4, mapOf("enabled" to true)).enabled)
    }

    @Test fun parseHour_handlesAmPmAnd24h() {
        assertEquals(6, JobFilterEngine.parseHour("06:30 AM"))
        assertEquals(17, JobFilterEngine.parseHour("5:00 PM"))
        assertEquals(0, JobFilterEngine.parseHour("12:15 AM"))
        assertEquals(12, JobFilterEngine.parseHour("12:15 PM"))
        assertEquals(18, JobFilterEngine.parseHour("2026-10-22 18:05"))
        assertNull(JobFilterEngine.parseHour("pagi"))
        assertNull(JobFilterEngine.parseHour(null))
    }

    @Test fun step1Filter_categoryFareHourService() {
        val jobs = JobParser.parse(step1, list).jobs
        val kw = listOf("KLIA")
        fun ids(f: Step1Filter) = jobs.filter { JobFilterEngine.checkStep1(it, f).pass }.map { it.id }

        // Tiada penapis aktif: semua lulus
        assertEquals(listOf("A1", "A2", "A3"), ids(Step1Filter(kliaKeywords = kw)))
        // Kategori: To KLIA sahaja / Normal sahaja
        assertEquals(listOf("A1", "A3"), ids(Step1Filter(toKlia = true, kliaKeywords = kw)))
        assertEquals(listOf("A2"), ids(Step1Filter(normalOn = true, kliaKeywords = kw)))
        // Harga minimum KLIA
        assertEquals(listOf("A3"), ids(Step1Filter(toKlia = true, minFareKlia = 100.0, kliaKeywords = kw)))
        // Harga minimum Normal menapis job Normal sahaja
        assertEquals(emptyList<String>(), ids(Step1Filter(normalOn = true, minFareNormal = 50.0, kliaKeywords = kw)))
        // Servis berbilang pilihan
        assertEquals(listOf("A3"), ids(Step1Filter(serviceClasses = setOf(ServiceClass.PLUS), kliaKeywords = kw)))
        assertEquals(
            listOf("A1", "A2"),
            ids(Step1Filter(serviceClasses = setOf(ServiceClass.STANDARD), kliaKeywords = kw)),
        )
        // Masa: Pilih Masa
        assertEquals(listOf("A1"), ids(Step1Filter(timeAll = false, hours = setOf(6), kliaKeywords = kw)))
        assertEquals(listOf("A3"), ids(Step1Filter(timeAll = false, hours = setOf(17), kliaKeywords = kw)))
        assertEquals(emptyList<String>(), ids(Step1Filter(timeAll = false, hours = emptySet(), kliaKeywords = kw)))
    }

    @Test fun categoryOf_fromKliaUsesPickup() {
        val kw = listOf("KLIA")
        assertEquals(JobCategory.FROM_KLIA, JobFilterEngine.categoryOf(JobCard(id = "1", pickup = "KLIA T2", dropoff = "KL Sentral"), kw))
        assertEquals(JobCategory.TO_KLIA, JobFilterEngine.categoryOf(JobCard(id = "2", pickup = "Taman A", dropoff = "KLIA T1"), kw))
        assertEquals(JobCategory.NORMAL, JobFilterEngine.categoryOf(JobCard(id = "3", pickup = "Taman A", dropoff = "Mid Valley"), kw))
        assertEquals(JobCategory.NORMAL, JobFilterEngine.categoryOf(JobCard(id = "4", dropoff = "KLIA T1"), emptyList()))
    }

    @Test fun step1Filter_areaAvoidAndOnly() {
        val j = JobCard(id = "1", pickup = "Taman A", dropoff = "KLIA T1")
        assertFalse(JobFilterEngine.checkStep1(j, Step1Filter(areaOn = true, areaAvoid = true, areaKeywords = listOf("taman a"))).pass)
        assertTrue(JobFilterEngine.checkStep1(j, Step1Filter(areaOn = true, areaAvoid = false, areaKeywords = listOf("klia"))).pass)
        assertFalse(JobFilterEngine.checkStep1(j, Step1Filter(areaOn = true, areaAvoid = false, areaKeywords = listOf("ipoh"))).pass)
        // Tiada pickup/drop-off dalam data Step 1: ditangguh ke Step 2
        assertTrue(JobFilterEngine.checkStep1(JobCard(id = "2"), Step1Filter(areaOn = true, areaKeywords = listOf("x"))).pass)
    }

    @Test fun serviceClass_matchesCombinedLabels() {
        assertTrue(ServiceClass.STANDARD.pattern.containsMatchIn("Advance Standard | Plus"))
        assertTrue(ServiceClass.PLUS.pattern.containsMatchIn("Advance Standard | Plus"))
        assertFalse(ServiceClass.PLUS.pattern.containsMatchIn("Standard"))
        assertTrue(ServiceClass.PREMIUM_EV.pattern.containsMatchIn("Premium | EV"))
        assertTrue(ServiceClass.EV_6_SEATS.pattern.containsMatchIn("EV 6 Seats"))
    }

    @Test fun step1Filter_reportsReasonForUnreadableValues() {
        val j = JobCard(id = "Z", fare = "percuma", time = "pagi", serviceType = "Normal")
        assertFalse(JobFilterEngine.checkStep1(j, Step1Filter(normalOn = true, minFareNormal = 1.0)).pass)
        assertFalse(JobFilterEngine.checkStep1(j, Step1Filter(timeAll = false, hours = setOf(6))).pass)
        // Tanpa tapisan aktif, nilai yang tak boleh dibaca tidak menghalang.
        assertTrue(JobFilterEngine.checkStep1(j, Step1Filter()).pass)
    }

    @Test fun criteria_locationAndKm() {
        val d = JobDetail(pickup = "Taman A, KL", dropoff = "KLIA T1", distanceKm = 12.5)
        assertTrue(JobFilterEngine.checkCriteria(d, Criteria()).pass)
        assertTrue(JobFilterEngine.checkCriteria(d, Criteria(maxKm = 20.0)).pass)
        assertFalse(JobFilterEngine.checkCriteria(d, Criteria(maxKm = 10.0)).pass)
        assertFalse(JobFilterEngine.checkCriteria(d, Criteria(avoidLocations = listOf("taman a"))).pass)
        assertTrue(JobFilterEngine.checkCriteria(d, Criteria(includeLocations = listOf("klia"))).pass)
        assertFalse(JobFilterEngine.checkCriteria(d, Criteria(includeLocations = listOf("ipoh"))).pass)
        // km tiada tetapi had km diset: tak boleh disahkan -> tak sesuai
        assertFalse(JobFilterEngine.checkCriteria(d.copy(distanceKm = null), Criteria(maxKm = 20.0)).pass)
    }

    @Test fun template_replacesAndFlagsUnresolved() {
        assertEquals("/job/a%20b", Template.apply("/job/{jobId}", mapOf("jobId" to "a b"), urlEncode = true))
        assertEquals("""{"id":"7"}""", Template.apply("""{"id":"{jobId}"}""", mapOf("jobId" to "7")))
        assertEquals(listOf("{jobType}"), Template.unresolved("x/{jobType}"))
        assertTrue(Template.unresolved("""{"a":1}""").isEmpty())
    }

    @Test fun parse_handlesBomAndLargeBody() {
        val cfg = StepConfig.fromMap("step1_job_list", mapOf("listPath" to "d.items", "fieldMap" to mapOf("id" to "ref")))
        val items = (1..2000).joinToString(",") { """{"ref":"R$it","pad":"xxxxxxxxxxxxxxxxxxxx"}""" }
        val body = "\uFEFF" + """{"d":{"items":[$items]}}"""
        assertTrue(body.length > 20_000)
        val r = JobParser.parse(cfg, body)
        assertNull(r.error)
        assertEquals(2000, r.jobs.size)
    }

    @Test fun parse_nonJsonGivesHint() {
        val cfg = StepConfig.fromMap("step1_job_list", mapOf("fieldMap" to mapOf("id" to "ref")))
        val r = JobParser.parse(cfg, "<html><body>login</body></html>")
        assertTrue(r.error!!.contains("HTML"))
    }

    @Test fun parse_stripsHtmlTagsFromFields() {
        val cfg = StepConfig.fromMap(
            "step1_job_list",
            mapOf("listPath" to "items", "fieldMap" to mapOf("id" to "ref", "time" to "t", "pickup" to "p")),
        )
        val body = """{"items":[{"ref":"R1","t":"<b>05:30 PM</b>","p":"<img src=\"x\"></img> Tempat A"}]}"""
        val job = JobParser.parse(cfg, body).jobs.single()
        assertEquals("05:30 PM", job.time)
        assertEquals("Tempat A", job.pickup)
    }
}
