package my.mysolat.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import my.mysolat.WaktuSolatApiException
import my.mysolat.WaktuSolatClient
import my.mysolat.model.PrayerTime
import my.mysolat.model.ZoneInfo
import java.time.LocalDate

class PrayerTimesViewModel : ViewModel() {
    private val client = WaktuSolatClient()

    var zones by mutableStateOf<List<ZoneInfo>>(emptyList())
        private set
    var selectedZone by mutableStateOf("SGR01")
        private set
    var prayerTime by mutableStateOf<PrayerTime?>(null)
        private set
    var source by mutableStateOf("")
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        load {
            zones = client.getZones()
            loadZone(selectedZone)
        }
    }

    fun selectZone(zone: String) {
        selectedZone = zone
        load { loadZone(zone) }
    }

    fun useLocation(latitude: Double, longitude: Double) {
        load {
            prayerTime = client.getPrayerTimeByDateGps(latitude, longitude, LocalDate.now())
            source = "GPS %.4f, %.4f".format(latitude, longitude)
        }
    }

    fun showError(message: String) {
        error = message
    }

    private suspend fun loadZone(zone: String) {
        prayerTime = client.getPrayerTimeByDate(zone, LocalDate.now())
        source = zone
    }

    private fun load(block: suspend () -> Unit) {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                block()
            } catch (e: WaktuSolatApiException) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }
}
