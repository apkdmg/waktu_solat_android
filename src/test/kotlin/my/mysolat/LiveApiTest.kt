package my.mysolat

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Hits the real api.waktusolat.app. Run with `./gradlew liveTest`. */
@Tag("live")
class LiveApiTest {
    private val client = WaktuSolatClient()

    @Test
    fun `fetches states and zones`() = runBlocking {
        assertTrue(client.getStates().isNotEmpty())
        assertTrue(client.getZones().any { it.jakimCode == "SGR01" })
    }

    @Test
    fun `fetches prayer times by zone and GPS`() = runBlocking {
        val byZone = client.getPrayerTimesByZone("SGR01", year = 2025, month = 3)
        assertEquals("SGR01", byZone.zone)
        assertEquals(31, byZone.prayerTime.size)
        assertNotNull(byZone.prayerTime[0].isyraq)

        val byGps = client.getPrayerTimesByGps(3.0738, 101.5183)
        assertTrue(byGps.prayerTime.isNotEmpty())
    }

    @Test
    fun `fetches a single date`() = runBlocking {
        val today = LocalDate.now()
        val prayerTime = client.getPrayerTimeByDate("SGR01", today)
        assertEquals(today.dayOfMonth, prayerTime?.day)
    }

    @Test
    fun `reports unknown zone`() = runBlocking {
        val e = assertFailsWith<WaktuSolatApiException> { client.getPrayerTimesByZone("XXX99") }
        assertEquals(404, e.statusCode)
    }
}
