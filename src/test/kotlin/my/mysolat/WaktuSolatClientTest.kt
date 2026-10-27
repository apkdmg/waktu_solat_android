package my.mysolat

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import java.time.LocalDate
import my.mysolat.model.WaktuState
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WaktuSolatClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: WaktuSolatClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = WaktuSolatClient(OkHttpClient(), server.url("/").toString())
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueue(body: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    private fun enqueueDisconnect() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
    }

    private fun assertRequestPath(expected: String) {
        assertEquals(expected, server.takeRequest().path)
    }

    @Nested
    inner class GetStates {
        private val response = """
            [
              {"jakimCode":"JHR01","negeri":"Johor","daerah":"Pulau Aur dan Pulau Pemanggil"},
              {"jakimCode":"JHR02","negeri":"Johor","daerah":"Johor Bahru, Kota Tinggi, Mersing, Kulai"},
              {"jakimCode":"KDH01","negeri":"Kedah","daerah":"Kota Setar, Kubang Pasu, Pokok Sena"},
              {"jakimCode":"KDH02","negeri":"Kedah","daerah":"Kuala Muda, Yan, Pendang"}
            ]
        """

        @Test
        fun `groups zones by state`() = runTest {
            enqueue(response)

            val states = client.getStates()

            assertEquals(
                listOf(WaktuState("Johor", listOf("JHR01", "JHR02")), WaktuState("Kedah", listOf("KDH01", "KDH02"))),
                states,
            )
            assertRequestPath("/zones")
        }

        @Test
        fun `throws on error status code`() = runTest {
            enqueue("""{"message":"Server Error"}""", 500)

            val e = assertFailsWith<WaktuSolatApiException> { client.getStates() }
            assertEquals(500, e.statusCode)
        }

        @Test
        fun `throws on network error`() = runTest {
            enqueueDisconnect()

            val e = assertFailsWith<WaktuSolatApiException> { client.getStates() }
            assertContains(e.message, "Network error")
        }

        @Test
        fun `throws when response is not a list`() = runTest {
            enqueue("""{"jakimCode":"JHR01"}""")

            val e = assertFailsWith<WaktuSolatApiException> { client.getStates() }
            assertContains(e.message, "Expected a List")
        }
    }

    @Nested
    inner class GetZones {
        private val response = """
            [
              {"jakimCode":"jhr01","negeri":"JOHOR","daerah":"Pulau Aur dan Pulau Pemanggil"},
              {"jakimCode":"jhr02","negeri":"JOHOR","daerah":"Kota Tinggi, Mersing, Johor Bahru"},
              {"jakimCode":"kdh01","negeri":"KEDAH","daerah":"Kota Setar, Kubang Pasu, Pokok Sena"},
              {"jakimCode":"sgr01","negeri":"SELANGOR","daerah":"Gombak, Petaling, Sepang, Hulu Langat, Hulu Selangor, S.Alam"}
            ]
        """

        @Test
        fun `returns zones on success`() = runTest {
            enqueue(response)

            val zones = client.getZones()

            assertEquals(4, zones.size)
            assertEquals("jhr01", zones[0].jakimCode)
            assertEquals("JOHOR", zones[0].negeri)
            assertEquals("Pulau Aur dan Pulau Pemanggil", zones[0].daerah)
            assertEquals("sgr01", zones[3].jakimCode)
            assertRequestPath("/zones")
        }

        @Test
        fun `throws on error status code`() = runTest {
            enqueue("""{"message":"Not Found"}""", 404)

            val e = assertFailsWith<WaktuSolatApiException> { client.getZones() }
            assertEquals(404, e.statusCode)
            assertContains(e.message, "API Request failed with status 404")
        }

        @Test
        fun `throws on network error`() = runTest {
            enqueueDisconnect()

            assertFailsWith<WaktuSolatApiException> { client.getZones() }
        }
    }

    @Nested
    inner class GetPrayerTimesByZone {
        private val zone = "sgr01"
        private val success = """{
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
              }
            ]
        }"""

        @Test
        fun `returns SolatV2 on success`() = runTest {
            enqueue(success)

            val result = client.getPrayerTimesByZone(zone)

            assertEquals(zone, result.zone)
            assertEquals("JAKIM", result.origin)
            assertEquals(1, result.prayerTime.size)
            assertEquals("2025-03-01", result.prayerTime[0].date)
            assertRequestPath("/v2/solat/sgr01")
        }

        @Test
        fun `sends year and month params`() = runTest {
            enqueue(success)

            val result = client.getPrayerTimesByZone(zone, year = 2025, month = 3)

            assertEquals(zone, result.zone)
            assertRequestPath("/v2/solat/sgr01?year=2025&month=3")
        }

        @Test
        fun `throws on API error with 200 OK`() = runTest {
            enqueue("""{"status": "error", "message": "Error, Zone not found, Please use /zones"}""")

            val e = assertFailsWith<WaktuSolatApiException> { client.getPrayerTimesByZone(zone) }
            assertContains(e.message, "Zone not found")
            assertEquals("error", e.apiError?.status)
        }

        @Test
        fun `parses ApiError from error status code`() = runTest {
            enqueue("""{"status": "error", "message": "Zone not found"}""", 404)

            val e = assertFailsWith<WaktuSolatApiException> { client.getPrayerTimesByZone(zone) }
            assertEquals(404, e.statusCode)
            assertEquals("API Error: Zone not found", e.message)
            assertNotNull(e.apiError)
        }

        @Test
        fun `throws on server error`() = runTest {
            enqueue("Server Error", 500)

            val e = assertFailsWith<WaktuSolatApiException> { client.getPrayerTimesByZone(zone) }
            assertEquals(500, e.statusCode)
            assertContains(e.message, "API Request failed with status 500")
        }

        @Test
        fun `throws on network error`() = runTest {
            enqueueDisconnect()

            val e = assertFailsWith<WaktuSolatApiException> { client.getPrayerTimesByZone(zone) }
            assertContains(e.message, "Network error")
        }

        @Test
        fun `throws on invalid JSON`() = runTest {
            enqueue("invalid json")

            val e = assertFailsWith<WaktuSolatApiException> { client.getPrayerTimesByZone(zone) }
            assertContains(e.message, "Failed to parse API response")
        }

        @Test
        fun `throws on malformed success JSON`() = runTest {
            enqueue("""{"zone": "sgr01", "origin": "JAKIM"}""")

            val e = assertFailsWith<WaktuSolatApiException> { client.getPrayerTimesByZone(zone) }
            assertContains(e.message, "Failed to parse SolatV2 response")
        }
    }

    @Nested
    inner class GetPrayerTimesByGps {
        private val lat = 3.0738
        private val long = 101.5183
        private val success = """{
            "zone": "WLP01",
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
              }
            ]
        }"""

        @Test
        fun `returns SolatV2 on success`() = runTest {
            enqueue(success)

            val result = client.getPrayerTimesByGps(lat, long)

            assertEquals("WLP01", result.zone)
            assertEquals("JAKIM", result.origin)
            assertEquals(1, result.prayerTime.size)
            assertEquals("2025-03-01", result.prayerTime[0].date)
            assertRequestPath("/v2/solat/gps/3.0738/101.5183")
        }

        @Test
        fun `sends year and month params`() = runTest {
            enqueue(success)

            client.getPrayerTimesByGps(lat, long, year = 2025, month = 3)

            assertRequestPath("/v2/solat/gps/3.0738/101.5183?year=2025&month=3")
        }

        @Test
        fun `throws on API error with 200 OK`() = runTest {
            enqueue("""{"status": "error", "message": "Invalid coordinates or no data available."}""")

            val e = assertFailsWith<WaktuSolatApiException> { client.getPrayerTimesByGps(lat, long) }
            assertContains(e.message, "Invalid coordinates")
        }

        @Test
        fun `throws on server error`() = runTest {
            enqueue("Internal Server Error", 500)

            val e = assertFailsWith<WaktuSolatApiException> { client.getPrayerTimesByGps(lat, long) }
            assertEquals(500, e.statusCode)
            assertContains(e.message, "API Request failed with status 500")
        }

        @Test
        fun `throws on network error`() = runTest {
            enqueueDisconnect()

            val e = assertFailsWith<WaktuSolatApiException> { client.getPrayerTimesByGps(lat, long) }
            assertContains(e.message, "Network error")
        }

        @Test
        fun `throws on invalid JSON`() = runTest {
            enqueue("not json")

            val e = assertFailsWith<WaktuSolatApiException> { client.getPrayerTimesByGps(lat, long) }
            assertContains(e.message, "Failed to parse API response")
        }

        @Test
        fun `throws on malformed success JSON`() = runTest {
            enqueue("""{"zone": "WLP01", "origin": "JAKIM"}""")

            val e = assertFailsWith<WaktuSolatApiException> { client.getPrayerTimesByGps(lat, long) }
            assertContains(e.message, "Failed to parse SolatV2")
        }
    }

    @Nested
    inner class GetPrayerTimeByDate {
        // Mirrors the real API: no "date" field, "day" is the day of the month.
        private val month = """{
            "zone": "SGR01",
            "prayers": [
              {"day": 1, "hijri": "1446-08-30", "fajr": 1740781020, "syuruk": 1740784920},
              {"day": 2, "hijri": "1446-09-01", "fajr": 1740867360, "syuruk": 1740871320}
            ]
        }"""

        @Test
        fun `falls back to day of month`() = runTest {
            enqueue(month)

            val result = client.getPrayerTimeByDate("SGR01", LocalDate.of(2025, 3, 2))

            assertEquals("1446-09-01", result?.hijri)
            assertRequestPath("/v2/solat/SGR01?year=2025&month=3")
        }

        @Test
        fun `prefers matching date string`() = runTest {
            enqueue(
                """{"zone": "SGR01", "prayers": [
                    {"day": 2, "hijri": "wrong"},
                    {"day": 9, "date": "2025-03-02", "hijri": "right"}
                ]}"""
            )

            val result = client.getPrayerTimeByDate("SGR01", LocalDate.of(2025, 3, 2))

            assertEquals("right", result?.hijri)
        }

        @Test
        fun `returns null when day is missing`() = runTest {
            enqueue(month)

            assertNull(client.getPrayerTimeByDate("SGR01", LocalDate.of(2025, 3, 15)))
        }

        @Test
        fun `GPS variant requests the date's month`() = runTest {
            enqueue(month)

            val result = client.getPrayerTimeByDateGps(3.0738, 101.5183, LocalDate.of(2025, 3, 1))

            assertEquals(1740781020, result?.fajr)
            assertRequestPath("/v2/solat/gps/3.0738/101.5183?year=2025&month=3")
        }
    }
}
