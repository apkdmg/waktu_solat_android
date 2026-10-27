package my.mysolat.model

import kotlinx.serialization.json.JsonObject

/**
 * Represents an error response from the Waktu Solat API.
 *
 * Typically used when the API returns `{"status": "error", "message": "..."}`
 * in the response body, either with a 200 OK or an error status code.
 */
public data class ApiError(
    /** The status indication, usually "error". */
    val status: String,
    /** The descriptive error message from the API. */
    val message: String,
) {
    internal companion object {
        fun fromJson(json: JsonObject): ApiError {
            val status = json.stringOrNull("status")
            val message = json.stringOrNull("message")
            if (status == null || message == null) {
                throw JsonFormatException("Invalid JSON structure for ApiError")
            }
            return ApiError(status = status, message = message)
        }
    }
}
