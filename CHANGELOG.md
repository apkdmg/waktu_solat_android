## 1.0.0

* First release of the Android/Kotlin port of `waktu_solat_lib`.
* `WaktuSolatClient` with coroutine `suspend` methods:
  * `getStates`, `getZones`
  * `getPrayerTimesByZone`, `getPrayerTimesByGps`
  * `getPrayerTimeByDate`, `getPrayerTimeByDateGps`
* `getStates` is built from `/zones` grouped by state, because the API no longer serves `/v2/negeri`.
* Automatic Imsak (Fajr − 10 min, when missing) and Isyraq (Syuruk + 15 min) calculation.
* `WaktuSolatApiException` for API, network and parsing errors.
* Jetpack Compose example app.
* Published through JitPack as `com.github.apkdmg:waktu_solat_android`.
