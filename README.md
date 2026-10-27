# WaktuSolatAndroid

`WaktuSolatAndroid` is a Kotlin library for Android (and any Kotlin/JVM project) to fetch Malaysia prayer times from [api.waktusolat.app](https://api.waktusolat.app/docs).

It is the Android counterpart of the Flutter [`waktu_solat_lib`](https://github.com/apkdmg/waktu_solat_lib) and Swift [`WaktuSolatSwift`](https://github.com/apkdmg/waktu_solat_swift) packages.

## Features

- Fetch all states with their zone codes
- Fetch all zones with daerah/negeri (`/zones`)
- Fetch monthly prayer times by zone (`/v2/solat/{zone}`)
- Fetch monthly prayer times by GPS (`/v2/solat/gps/{lat}/{long}`)
- Convenience methods for a single date
- API/network/parsing error handling with `WaktuSolatApiException`
- Coroutine `suspend` API; cancelling the coroutine cancels the HTTP request
- Auto-calculates:
  - `imsak` = `fajr - 10 minutes` if missing
  - `isyraq` = `syuruk + 15 minutes`

## Requirements

- Android `minSdk 26`+ (the date helpers use `java.time.LocalDate`). For lower `minSdk`, enable [core library desugaring](https://developer.android.com/studio/write/java8-support#library-desugaring).
- Kotlin 2.x with coroutines
- Java 11+ bytecode

Transitive dependencies: OkHttp 4, kotlinx-coroutines-core, kotlinx-serialization-json.

## Installation (Gradle, via JitPack)

Add JitPack to your repositories in `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

Then add the dependency to your app module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.apkdmg:waktu_solat_android:1.0.0")
}
```

<details>
<summary>Groovy (<code>settings.gradle</code> / <code>build.gradle</code>)</summary>

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}

dependencies {
    implementation 'com.github.apkdmg:waktu_solat_android:1.0.0'
}
```
</details>

Your app needs the internet permission:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## Quick Start

```kotlin
import my.mysolat.WaktuSolatApiException
import my.mysolat.WaktuSolatClient

val client = WaktuSolatClient()

lifecycleScope.launch {
    try {
        val result = client.getPrayerTimesByZone("SGR01")
        Log.d("Solat", "Zone: ${result.zone}, days returned: ${result.prayerTime.size}")
    } catch (e: WaktuSolatApiException) {
        Log.e("Solat", "API error: ${e.message}")
    }
}
```

All methods are main-safe: network I/O runs on OkHttp's own threads, so you can call them from `viewModelScope` or `lifecycleScope` directly.

To reuse your app's OkHttp setup (interceptors, timeouts, cache), pass it in:

```kotlin
val client = WaktuSolatClient(httpClient = myOkHttpClient)
```

## Guide: How To Use

### 1. Get all states

```kotlin
val states = client.getStates()
states.forEach { println("${it.negeri}: ${it.zones}") }
```

### 2. Get all zones

```kotlin
val zones = client.getZones()
zones.take(5).forEach { println("${it.jakimCode} - ${it.daerah} (${it.negeri})") }
```

### 3. Get monthly prayer times by zone

```kotlin
val data = client.getPrayerTimesByZone("SGR01", year = 2026, month = 2)

data.prayerTime.firstOrNull()?.let { firstDay ->
    println(firstDay.hijri)
    println(firstDay.fajr)
    println(firstDay.maghrib)
}
```

### 4. Get monthly prayer times by GPS

```kotlin
val data = client.getPrayerTimesByGps(3.1390, 101.6869)
println("Detected zone: ${data.zone}")
```

### 5. Get prayer time for one date

```kotlin
val today = LocalDate.now()

client.getPrayerTimeByDate("SGR01", today)?.let {
    println("Today Fajr: ${it.fajr}")
}

client.getPrayerTimeByDateGps(3.1390, 101.6869, today)?.let {
    println("Today Maghrib: ${it.maghrib}")
}
```

### 6. Convert API unix timestamps to readable time

API times are unix seconds. Each one also has an `Instant` property (`fajrInstant`, `maghribInstant`, …):

```kotlin
val formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Asia/Kuala_Lumpur"))

fun formatTime(instant: Instant?): String = instant?.let(formatter::format) ?: "--:--"

println(formatTime(prayerTime.fajrInstant))
```

## Error Handling

All failures (HTTP errors, network errors, unexpected JSON) throw `WaktuSolatApiException`:

```kotlin
try {
    client.getPrayerTimesByZone("INVALID_ZONE")
} catch (e: WaktuSolatApiException) {
    println(e.message)
    println(e.statusCode) // e.g. 404, or null for network errors
    println(e.apiError)   // parsed {"status": "error", "message": ...} body, if any
}
```

## Main Types

All in package `my.mysolat` / `my.mysolat.model`:

- `WaktuSolatClient`
- `WaktuState`
- `ZoneInfo`
- `PrayerTime`
- `SolatV2`
- `ApiError`
- `WaktuSolatApiException`

## Example App

A Jetpack Compose example app lives in [`example/`](example). It builds this library from source through a Gradle composite build (`includeBuild("..")`).

Open `example/` in Android Studio and run the `app` configuration, or:

```bash
cd example && ./gradlew :app:installDebug
```

## Development

```bash
./gradlew test              # unit tests (MockWebServer)
./gradlew liveTest          # tests against the real API
./gradlew publishToMavenLocal
```

### Releasing

1. Update `version` in `build.gradle.kts` and `CHANGELOG.md`.
2. Tag and push: `git tag 1.0.0 && git push origin 1.0.0`.
3. Open [jitpack.io/#apkdmg/waktu_solat_android](https://jitpack.io/#apkdmg/waktu_solat_android) and click **Get it** on the tag to trigger the first build.

## License

MIT. See [LICENSE](LICENSE).
