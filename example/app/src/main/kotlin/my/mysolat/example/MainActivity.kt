package my.mysolat.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import my.mysolat.model.PrayerTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    PrayerTimesScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerTimesScreen(viewModel: PrayerTimesViewModel = viewModel()) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) requestLocation(context, viewModel)
    }

    Column(
        Modifier.safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Waktu Solat", style = MaterialTheme.typography.headlineMedium)

        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = viewModel.selectedZone,
                onValueChange = {},
                readOnly = true,
                label = { Text("Zone") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                viewModel.zones.forEach { zone ->
                    DropdownMenuItem(
                        text = { Text("${zone.jakimCode} – ${zone.daerah}") },
                        onClick = {
                            expanded = false
                            viewModel.selectZone(zone.jakimCode)
                        },
                    )
                }
            }
        }

        Button(onClick = {
            val permission = Manifest.permission.ACCESS_COARSE_LOCATION
            if (ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestLocation(context, viewModel)
            } else {
                permissionLauncher.launch(permission)
            }
        }) {
            Text("Use my location")
        }

        if (viewModel.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        viewModel.prayerTime?.let { PrayerTimeCard(it, viewModel.source) }
    }
}

@Composable
private fun PrayerTimeCard(prayerTime: PrayerTime, source: String) {
    val rows = listOf(
        "Imsak" to prayerTime.imsak,
        "Subuh" to prayerTime.fajr,
        "Syuruk" to prayerTime.syuruk,
        "Isyraq" to prayerTime.isyraq,
        "Zohor" to prayerTime.dhuhr,
        "Asar" to prayerTime.asr,
        "Maghrib" to prayerTime.maghrib,
        "Isyak" to prayerTime.isha,
    )
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$source · Hijri ${prayerTime.hijri}", style = MaterialTheme.typography.titleMedium)
            rows.forEach { (name, time) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name)
                    Text(time?.let(::formatTime) ?: "–")
                }
            }
        }
    }
}

private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.of("Asia/Kuala_Lumpur"))

private fun formatTime(epochSeconds: Long): String = timeFormatter.format(Instant.ofEpochSecond(epochSeconds))

@SuppressLint("MissingPermission") // Checked by the caller.
private fun requestLocation(context: Context, viewModel: PrayerTimesViewModel) {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = locationManager.getProviders(true)

    providers.firstNotNullOfOrNull { locationManager.getLastKnownLocation(it) }?.let {
        viewModel.useLocation(it.latitude, it.longitude)
        return
    }

    val provider = providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER }
        ?: return viewModel.showError("Location is turned off")
    LocationManagerCompat.getCurrentLocation(
        locationManager,
        provider,
        null as android.os.CancellationSignal?,
        ContextCompat.getMainExecutor(context),
    ) { location: android.location.Location? ->
        if (location != null) {
            viewModel.useLocation(location.latitude, location.longitude)
        } else {
            viewModel.showError("Could not get your location")
        }
    }
}
