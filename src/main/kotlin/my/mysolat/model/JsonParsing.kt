package my.mysolat.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Thrown when a JSON payload does not match the expected model shape. */
internal class JsonFormatException(message: String) : Exception(message)

/** Returns the string value of [key], or `null` if absent, JSON `null`, or not a string. */
internal fun JsonObject.stringOrNull(key: String): String? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    return if (primitive.isString) primitive.content else null
}

/** Returns the integer value of [key], or `null` if absent, JSON `null`, or not an integer. */
internal fun JsonObject.integerOrNull(key: String): Long? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    return if (primitive.isString) null else primitive.longOrNull
}

internal fun JsonElement?.isJsonNull(): Boolean = this == null || this is JsonNull

internal fun JsonElement.typeName(): String = when (this) {
    is JsonObject -> "Object"
    is JsonArray -> "List"
    is JsonNull -> "Null"
    is JsonPrimitive -> when {
        isString -> "String"
        longOrNull != null -> "int"
        content == "true" || content == "false" -> "bool"
        else -> "double"
    }
}
