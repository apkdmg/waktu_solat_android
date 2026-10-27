package my.mysolat

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import my.mysolat.model.ApiError
import my.mysolat.model.JsonFormatException
import my.mysolat.model.PrayerTime
import my.mysolat.model.SolatV2
import my.mysolat.model.WaktuState
import my.mysolat.model.ZoneInfo
import my.mysolat.model.stringOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * A client for interacting with the Waktu Solat API v2 (https://api.waktusolat.app/docs).
 *
 * Provides methods to fetch prayer times, state lists, and zone lists.
 * All methods are main-safe suspend functions; network I/O runs on OkHttp's dispatcher.
 *
 * @param httpClient The OkHttp client used for requests. Pass your app's shared
 *   instance to reuse its connection pool, interceptors and timeouts.
 * @param baseUrl The API base URL. Override only for testing.
 */
public class WaktuSolatClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
    baseUrl: String = DEFAULT_BASE_URL,
) {
    private val baseUrl: HttpUrl = baseUrl.toHttpUrl()

    /**
     * Fetches the list of all Malaysian states and their corresponding zones.
     *
     * The API no longer has a states endpoint (`/v2/negeri` returns 404), so this
     * groups the `/zones` response by [ZoneInfo.negeri], in the order the API returns them.
     *
     * @throws WaktuSolatApiException on failure.
     */
    public suspend fun getStates(): List<WaktuState> =
        getZones()
            .groupBy({ it.negeri }, { it.jakimCode })
            .map { (negeri, zones) -> WaktuState(negeri = negeri, zones = zones) }

    /**
     * Fetches the list of all prayer zone information.
     *
     * Corresponds to the `/zones` endpoint.
     * See zones visually at https://peta.waktusolat.app
     *
     * @throws WaktuSolatApiException on failure.
     */
    public suspend fun getZones(): List<ZoneInfo> {
        val response = getRequest(listOf("zones"))

        if (response !is JsonArray) {
            throw WaktuSolatApiException(
                "Unexpected response format received for /zones. Expected a List of zone objects."
            )
        }

        return try {
            response.map { ZoneInfo.fromJson(it.asObject("zone list")) }
        } catch (e: JsonFormatException) {
            throw WaktuSolatApiException("Failed to parse zone info list response: ${e.message}", cause = e)
        }
    }

    /**
     * Fetches prayer times for a specific zone, optionally for a given year and month.
     *
     * Corresponds to the `/v2/solat/{zone}` endpoint.
     *
     * @param zone The zone code (e.g., "SGR01"). Valid zones come from [getZones] or [getStates].
     * @param year The year (e.g., 2025). If omitted, the API defaults to the current year.
     * @param month The month (1-12). If omitted, the API defaults to the current month.
     * @throws WaktuSolatApiException on failure (e.g., invalid zone, network error).
     */
    public suspend fun getPrayerTimesByZone(
        zone: String,
        year: Int? = null,
        month: Int? = null,
    ): SolatV2 = getSolatV2(listOf("v2", "solat", zone), year, month)

    /**
     * Fetches prayer times based on GPS coordinates, optionally for a given year and month.
     *
     * Corresponds to the `/v2/solat/gps/{lat}/{long}` endpoint.
     *
     * @param latitude The latitude coordinate.
     * @param longitude The longitude coordinate.
     * @param year The year (e.g., 2025). If omitted, the API defaults to the current year.
     * @param month The month (1-12). If omitted, the API defaults to the current month.
     * @throws WaktuSolatApiException on failure (e.g., invalid coordinates, network error).
     */
    public suspend fun getPrayerTimesByGps(
        latitude: Double,
        longitude: Double,
        year: Int? = null,
        month: Int? = null,
    ): SolatV2 = getSolatV2(
        listOf("v2", "solat", "gps", latitude.toString(), longitude.toString()),
        year,
        month,
    )

    /**
     * Fetches prayer times for a specific date.
     *
     * Fetches the month containing [date] and returns that day's entry.
     *
     * @param zone The zone code (e.g., "SGR01").
     * @param date The date to look up.
     * @return The prayer times for [date], or `null` if the API returned none for that day.
     * @throws WaktuSolatApiException on failure (e.g., invalid zone, network error).
     */
    public suspend fun getPrayerTimeByDate(zone: String, date: LocalDate): PrayerTime? {
        val solatV2 = getPrayerTimesByZone(zone, year = date.year, month = date.monthValue)
        return solatV2.findDay(date)
    }

    /**
     * Fetches prayer times for a specific date using GPS coordinates.
     *
     * Fetches the month containing [date] and returns that day's entry.
     *
     * @param latitude The latitude coordinate.
     * @param longitude The longitude coordinate.
     * @param date The date to look up.
     * @return The prayer times for [date], or `null` if the API returned none for that day.
     * @throws WaktuSolatApiException on failure (e.g., invalid coordinates, network error).
     */
    public suspend fun getPrayerTimeByDateGps(
        latitude: Double,
        longitude: Double,
        date: LocalDate,
    ): PrayerTime? {
        val solatV2 = getPrayerTimesByGps(latitude, longitude, year = date.year, month = date.monthValue)
        return solatV2.findDay(date)
    }

    private suspend fun getSolatV2(pathSegments: List<String>, year: Int?, month: Int?): SolatV2 {
        val queryParameters = buildMap {
            if (year != null) put("year", year.toString())
            if (month != null) put("month", month.toString())
        }

        val response = getRequest(pathSegments, queryParameters)

        return try {
            SolatV2.fromJson(response.asObject("SolatV2 payload"))
        } catch (e: JsonFormatException) {
            throw WaktuSolatApiException("Failed to parse SolatV2 response: ${e.message}", cause = e)
        }
    }

    /**
     * Performs a GET request and handles common errors.
     *
     * Throws [WaktuSolatApiException] for:
     *   - Status codes >= 400 (parsing [ApiError] if possible).
     *   - `status: "error"` in the JSON response body (even with 200 OK).
     *   - Network errors ([IOException]).
     *   - JSON decoding errors.
     */
    private suspend fun getRequest(
        pathSegments: List<String>,
        queryParameters: Map<String, String> = emptyMap(),
    ): JsonElement {
        val url = baseUrl.newBuilder().apply {
            pathSegments.forEach { addPathSegment(it) }
            queryParameters.forEach { (name, value) -> addQueryParameter(name, value) }
        }.build()

        val (statusCode, body) = try {
            httpClient.newCall(Request.Builder().url(url).get().build()).await()
        } catch (e: IOException) {
            throw WaktuSolatApiException("Network error: $e", cause = e)
        }

        if (statusCode >= 400) {
            val apiError = runCatching { ApiError.fromJson(json.parseToJsonElement(body).asObject("error")) }
                .getOrNull()
            if (apiError != null) {
                throw WaktuSolatApiException(
                    "API Error: ${apiError.message}",
                    statusCode = statusCode,
                    apiError = apiError,
                )
            }
            throw WaktuSolatApiException(
                "API Request failed with status $statusCode. Response: $body",
                statusCode = statusCode,
            )
        }

        val jsonResponse = try {
            json.parseToJsonElement(body)
        } catch (e: SerializationException) {
            throw WaktuSolatApiException("Failed to parse API response: ${e.message}", cause = e)
        }

        // Handle API errors returned with 200 OK ({"status": "error", ...})
        if (jsonResponse is JsonObject && jsonResponse.stringOrNull("status") == "error") {
            val apiError = try {
                ApiError.fromJson(jsonResponse)
            } catch (e: JsonFormatException) {
                throw WaktuSolatApiException("An unexpected error occurred: ${e.message}", cause = e)
            }
            throw WaktuSolatApiException(apiError.message, statusCode = statusCode, apiError = apiError)
        }

        return jsonResponse
    }

    /** Executes the call asynchronously, cancelling it if the coroutine is cancelled. */
    private suspend fun Call.await(): Pair<Int, String> = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    response.use { it.code to it.body?.string().orEmpty() }
                } catch (e: IOException) {
                    continuation.resumeWithException(e)
                    return
                }
                continuation.resume(result)
            }
        })
    }

    public companion object {
        /** The base URL for the Waktu Solat API. */
        public const val DEFAULT_BASE_URL: String = "https://api.waktusolat.app"

        private val json = Json
        private val isoDate: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        private fun JsonElement.asObject(context: String): JsonObject =
            this as? JsonObject ?: throw JsonFormatException("Expected object inside $context")

        /** Matches by Gregorian date string first, then falls back to day of month. */
        private fun SolatV2.findDay(date: LocalDate): PrayerTime? {
            val dateString = date.format(isoDate)
            return prayerTime.firstOrNull { it.date == dateString }
                ?: prayerTime.firstOrNull { it.day == date.dayOfMonth }
        }
    }
}
