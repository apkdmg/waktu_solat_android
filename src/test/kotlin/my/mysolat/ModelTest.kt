package my.mysolat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import my.mysolat.model.ApiError
import my.mysolat.model.JsonFormatException
import my.mysolat.model.PrayerTime
import my.mysolat.model.SolatV2
import my.mysolat.model.ZoneInfo
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ModelTest {
    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    @Test
    fun `PrayerTime fromJson parses valid JSON correctly`() {
        val prayerTime = PrayerTime.fromJson(
            obj(
                """{
                  "hijri": "1446-09-01",
                  "date": "2025-03-01",
                  "day": 6,
                  "imsak": 1425480000,
                  "fajr": 1425480480,
                  "syuruk": 1425485460,
                  "dhuhr": 1425507480,
                  "asr": 1425518340,
                  "maghrib": 1425529800,
                  "isha": 1425533940
                }"""
            )
        )

        assertEquals("1446-09-01", prayerTime.hijri)
        assertEquals("2025-03-01", prayerTime.date)
        assertEquals(6, prayerTime.day)
        assertEquals(1425480000, prayerTime.imsak)
        assertEquals(1425480480, prayerTime.fajr)
        assertEquals(1425485460, prayerTime.syuruk)
        assertEquals(1425507480, prayerTime.dhuhr)
        assertEquals(1425518340, prayerTime.asr)
        assertEquals(1425529800, prayerTime.maghrib)
        assertEquals(1425533940, prayerTime.isha)
        // syuruk + 15 minutes (900 seconds)
        assertEquals(1425486360, prayerTime.isyraq)
        assertEquals(Instant.ofEpochSecond(1425480480), prayerTime.fajrInstant)
    }

    @Test
    fun `PrayerTime calculates imsak from fajr when missing`() {
        val prayerTime = PrayerTime.fromJson(obj("""{"hijri": "1446-09-01", "day": 1, "fajr": 1000000}"""))

        assertEquals(1000000 - 600L, prayerTime.imsak)
        assertNull(prayerTime.date)
        assertNull(prayerTime.syuruk)
        assertNull(prayerTime.isyraq)
    }

    @Test
    fun `PrayerTime accepts numeric strings for times`() {
        val prayerTime = PrayerTime.fromJson(
            obj("""{"hijri": "1446-09-01", "day": 1, "fajr": "1000000", "syuruk": "2000000", "isha": null}""")
        )

        assertEquals(1000000, prayerTime.fajr)
        assertEquals(2000900, prayerTime.isyraq)
        assertNull(prayerTime.isha)
    }

    @Test
    fun `PrayerTime rejects invalid fields`() {
        assertFailsWith<JsonFormatException> { PrayerTime.fromJson(obj("""{"day": 1}""")) }
        assertFailsWith<JsonFormatException> { PrayerTime.fromJson(obj("""{"hijri": "x"}""")) }
        assertFailsWith<JsonFormatException> { PrayerTime.fromJson(obj("""{"hijri": "x", "day": 1, "date": 5}""")) }
        assertFailsWith<JsonFormatException> { PrayerTime.fromJson(obj("""{"hijri": "x", "day": 1, "fajr": "abc"}""")) }
        assertFailsWith<JsonFormatException> { PrayerTime.fromJson(obj("""{"hijri": "x", "day": 1, "fajr": true}""")) }
    }

    @Test
    fun `SolatV2 fromJson parses valid JSON correctly`() {
        val solatV2 = SolatV2.fromJson(
            obj(
                """{
                  "zone": "sgr01",
                  "origin": "JAKIM",
                  "prayers": [
                    {
                      "hijri": "1446-09-01",
                      "date": "2025-03-01",
                      "day": 6,
                      "imsak": 1425480000,
                      "fajr": 1425480480,
                      "syuruk": 1425485460,
                      "dhuhr": 1425507480,
                      "asr": 1425518340,
                      "maghrib": 1425529800,
                      "isha": 1425533940
                    },
                    {
                      "hijri": "1446-09-02",
                      "date": "2025-03-02",
                      "day": 0,
                      "imsak": 1425566400,
                      "fajr": 1425566880,
                      "syuruk": 1425571860,
                      "dhuhr": 1425593880,
                      "asr": 1425604740,
                      "maghrib": 1425616200,
                      "isha": 1425620340
                    }
                  ]
                }"""
            )
        )

        assertEquals("sgr01", solatV2.zone)
        assertEquals("JAKIM", solatV2.origin)
        assertEquals(2, solatV2.prayerTime.size)
        assertEquals("2025-03-01", solatV2.prayerTime[0].date)
        assertEquals(6, solatV2.prayerTime[0].day)
        assertEquals(0, solatV2.prayerTime[1].day)
        assertEquals(1425480480, solatV2.prayerTime[0].fajr)
        assertEquals(1425485460, solatV2.prayerTime[0].syuruk)
        assertEquals(1425486360, solatV2.prayerTime[0].isyraq)
        assertEquals(1425572760, solatV2.prayerTime[1].isyraq)
    }

    @Test
    fun `SolatV2 rejects missing prayers`() {
        assertFailsWith<JsonFormatException> { SolatV2.fromJson(obj("""{"zone": "sgr01", "origin": "JAKIM"}""")) }
    }

    @Test
    fun `ZoneInfo fromJson parses valid JSON correctly`() {
        val zone = ZoneInfo.fromJson(obj("""{"jakimCode": "JHR01", "negeri": "Johor", "daerah": "Pulau Aur"}"""))

        assertEquals(ZoneInfo("JHR01", "Johor", "Pulau Aur"), zone)
    }

    @Test
    fun `ApiError fromJson parses valid JSON correctly`() {
        val apiError = ApiError.fromJson(
            obj("""{"status": "error", "message": "Error, Zone not found, Please use /zones"}""")
        )

        assertEquals("error", apiError.status)
        assertEquals("Error, Zone not found, Please use /zones", apiError.message)
    }
}
