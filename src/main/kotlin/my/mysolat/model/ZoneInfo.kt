package my.mysolat.model

import kotlinx.serialization.json.JsonObject

/**
 * Represents zone information returned by the API.
 *
 * Contains the JAKIM code, the state (negeri), and the district/area (daerah).
 */
public data class ZoneInfo(
    /** The JAKIM zone code (e.g., "JHR01", "SGR02"). */
    val jakimCode: String,
    /** The name of the state (negeri) the zone belongs to. */
    val negeri: String,
    /** The name of the district or area (daerah) covered by the zone. */
    val daerah: String,
) {
    internal companion object {
        fun fromJson(json: JsonObject): ZoneInfo {
            val jakimCode = json.stringOrNull("jakimCode")
            val negeri = json.stringOrNull("negeri")
            val daerah = json.stringOrNull("daerah")
            if (jakimCode == null || negeri == null || daerah == null) {
                throw JsonFormatException("Invalid JSON format for ZoneInfo: $json")
            }
            return ZoneInfo(jakimCode = jakimCode, negeri = negeri, daerah = daerah)
        }
    }
}
