package my.mysolat

import my.mysolat.model.ApiError

/** Exception thrown for Waktu Solat API, network and parsing errors. */
public class WaktuSolatApiException(
    message: String,
    /** The HTTP status code associated with the error, if available. */
    public val statusCode: Int? = null,
    /** The detailed API error, if one was parsed from the response body. */
    public val apiError: ApiError? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {

    override val message: String get() = super.message.orEmpty()

    override fun toString(): String =
        "WaktuSolatApiException: $message" + (statusCode?.let { " (Status Code: $it)" } ?: "")
}
