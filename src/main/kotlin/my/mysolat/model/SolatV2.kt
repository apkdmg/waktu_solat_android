package my.mysolat.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Represents the response structure for prayer times fetched from the API
 * (endpoints `/v2/solat/{zone}` and `/v2/solat/gps/{lat}/{long}`).
 */
public data class SolatV2(
    /** The prayer zone code (e.g., "SGR01"). */
    val zone: String,
    /** The source or origin of the prayer time data (e.g., "JAKIM"). May be null. */
    val origin: String? = null,
    /** Prayer times, typically one entry per day of the requested month. */
    val prayerTime: List<PrayerTime>,
) {
    internal companion object {
        fun fromJson(json: JsonObject): SolatV2 {
            val zone = json.stringOrNull("zone")
                ?: throw JsonFormatException("Invalid or missing field (zone) in SolatV2 JSON")

            // API uses 'prayers', model uses 'prayerTime'
            val prayers = json["prayers"] as? JsonArray
                ?: throw JsonFormatException("Missing or invalid field: prayers in SolatV2 JSON")

            val prayerTimes = try {
                prayers.map { item ->
                    PrayerTime.fromJson(
                        item as? JsonObject
                            ?: throw JsonFormatException("Expected object inside prayers list")
                    )
                }
            } catch (e: JsonFormatException) {
                throw JsonFormatException("Failed to parse prayerTime list in SolatV2 JSON: ${e.message}")
            }

            return SolatV2(
                zone = zone,
                origin = json.stringOrNull("origin"),
                prayerTime = prayerTimes,
            )
        }
    }
}
