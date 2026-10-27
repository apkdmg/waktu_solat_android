package my.mysolat.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant

/**
 * Represents the prayer times for a single day.
 *
 * All prayer times are Unix timestamps (seconds since epoch).
 */
public data class PrayerTime(
    /** Hijri date string (format: YYYY-MM-DD). */
    val hijri: String,
    /** Gregorian date string (format: YYYY-MM-DD). May be null from some endpoints. */
    val date: String? = null,
    /** Day of the month. */
    val day: Int,
    /** Imsak time. Calculated as 10 minutes before Fajr when the API omits it. */
    val imsak: Long? = null,
    /** Fajr (Subuh) time. */
    val fajr: Long? = null,
    /** Syuruk time. */
    val syuruk: Long? = null,
    /** Dhuhr (Zohor) time. */
    val dhuhr: Long? = null,
    /** Asr time. */
    val asr: Long? = null,
    /** Maghrib time. */
    val maghrib: Long? = null,
    /** Isha time. */
    val isha: Long? = null,
    /** Isyraq time, calculated as 15 minutes after Syuruk. */
    val isyraq: Long? = null,
) {
    /** [imsak] as an [Instant], or `null` if unavailable. */
    val imsakInstant: Instant? get() = imsak?.let(Instant::ofEpochSecond)

    /** [fajr] as an [Instant], or `null` if unavailable. */
    val fajrInstant: Instant? get() = fajr?.let(Instant::ofEpochSecond)

    /** [syuruk] as an [Instant], or `null` if unavailable. */
    val syurukInstant: Instant? get() = syuruk?.let(Instant::ofEpochSecond)

    /** [isyraq] as an [Instant], or `null` if unavailable. */
    val isyraqInstant: Instant? get() = isyraq?.let(Instant::ofEpochSecond)

    /** [dhuhr] as an [Instant], or `null` if unavailable. */
    val dhuhrInstant: Instant? get() = dhuhr?.let(Instant::ofEpochSecond)

    /** [asr] as an [Instant], or `null` if unavailable. */
    val asrInstant: Instant? get() = asr?.let(Instant::ofEpochSecond)

    /** [maghrib] as an [Instant], or `null` if unavailable. */
    val maghribInstant: Instant? get() = maghrib?.let(Instant::ofEpochSecond)

    /** [isha] as an [Instant], or `null` if unavailable. */
    val ishaInstant: Instant? get() = isha?.let(Instant::ofEpochSecond)

    internal companion object {
        private val timeFields = listOf("imsak", "fajr", "syuruk", "dhuhr", "asr", "maghrib", "isha")

        fun fromJson(json: JsonObject): PrayerTime {
            val hijri = json.stringOrNull("hijri")
                ?: throw JsonFormatException("Invalid or missing String field: hijri in PrayerTime JSON")

            val dateElement = json["date"]
            if (!dateElement.isJsonNull() && json.stringOrNull("date") == null) {
                throw JsonFormatException(
                    "Invalid field type: date (must be String or null) in PrayerTime JSON"
                )
            }
            val date = json.stringOrNull("date")

            val day = json.integerOrNull("day")?.toInt()
                ?: throw JsonFormatException(
                    "Invalid or missing Int field: day in PrayerTime JSON " +
                        "(got ${json["day"]?.typeName() ?: "Null"})"
                )

            val times = timeFields.associateWith { field -> parseTime(json, field) }
            val fajr = times["fajr"]
            val syuruk = times["syuruk"]

            return PrayerTime(
                hijri = hijri,
                date = date,
                day = day,
                imsak = times["imsak"] ?: fajr?.minus(10 * 60),
                fajr = fajr,
                syuruk = syuruk,
                dhuhr = times["dhuhr"],
                asr = times["asr"],
                maghrib = times["maghrib"],
                isha = times["isha"],
                isyraq = syuruk?.plus(15 * 60),
            )
        }

        /** Parses a nullable time field, accepting an integer or a numeric string (legacy). */
        private fun parseTime(json: JsonObject, field: String): Long? {
            val element = json[field]
            if (element.isJsonNull()) return null

            json.integerOrNull(field)?.let { return it }

            if (element is JsonPrimitive && element.isString) {
                return element.content.toLongOrNull()
                    ?: throw JsonFormatException(
                        "Invalid non-integer String field: $field in PrayerTime JSON"
                    )
            }

            throw JsonFormatException(
                "Invalid or missing Int field: $field in PrayerTime JSON (got ${element!!.typeName()})"
            )
        }
    }
}
