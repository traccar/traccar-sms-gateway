package com.simplemobiletools.smsmessenger.extensions

import com.google.gson.*
import java.math.BigDecimal
import java.math.BigInteger


val JsonElement.optString: String?
    get() = safeConversion { asString }

val JsonElement.optLong: Long?
    get() = safeConversion { asLong }

val JsonElement.optBoolean: Boolean?
    get() = safeConversion { asBoolean }

val JsonElement.optFloat: Float?
    get() = safeConversion { asFloat }

val JsonElement.optDouble: Double?
    get() = safeConversion { asDouble }

val JsonElement.optJsonObject: JsonObject?
    get() = safeConversion { asJsonObject }

val JsonElement.optJsonArray: JsonArray?
    get() = safeConversion { asJsonArray }

val JsonElement.optJsonPrimitive: JsonPrimitive?
    get() = safeConversion { asJsonPrimitive }

val JsonElement.optInt: Int?
    get() = safeConversion { asInt }

val JsonElement.optBigDecimal: BigDecimal?
    get() = safeConversion { asBigDecimal }

val JsonElement.optBigInteger: BigInteger?
    get() = safeConversion { asBigInteger }

val JsonElement.optByte: Byte?
    get() = safeConversion { asByte }

val JsonElement.optShort: Short?
    get() = safeConversion { asShort }

val JsonElement.optJsonNull: JsonNull?
    get() = safeConversion { asJsonNull }

val JsonElement.optCharacter: Char?
    get() = safeConversion { asCharacter }

private fun <T> JsonElement.safeConversion(converter: () -> T?): T? {

    return try {
        converter()
    } catch (e: Exception) {
        null
    }
}

fun JsonObject.optGet(key: String): JsonElement? = get(key)

fun JsonObject.optGetJsonArray(key: String): JsonArray? = getAsJsonArray(key)

fun JsonObject.optGetJsonObject(key: String): JsonObject? = getAsJsonObject(key)

fun JsonObject.optGetJsonPrimitive(key: String): JsonPrimitive? = getAsJsonPrimitive(key)

fun JsonObject.optString(key: String) = optGet(key)?.asString

fun JsonObject.optLong(key: String) = optGet(key)?.asLong

fun JsonObject.optBoolean(key: String) = optGet(key)?.asBoolean

fun JsonObject.optFloat(key: String) = optGet(key)?.asFloat

fun JsonObject.optDouble(key: String) = optGet(key)?.asDouble

fun JsonObject.optJsonObject(key: String) = optGet(key)?.asJsonObject

fun JsonObject.optJsonArray(key: String) = optGet(key)?.asJsonArray

fun JsonObject.optJsonPrimitive(key: String) = optGet(key)?.asJsonPrimitive

fun JsonObject.optInt(key: String) = optGet(key)?.asInt

fun JsonObject.optBigDecimal(key: String) = optGet(key)?.asBigDecimal

fun JsonObject.optBigInteger(key: String) = optGet(key)?.asBigInteger

fun JsonObject.optByte(key: String) = optGet(key)?.asByte

fun JsonObject.optShort(key: String) = optGet(key)?.asShort

fun JsonObject.optJsonNull(key: String) = optGet(key)?.asJsonNull

fun JsonObject.optCharacter(key: String) = optGet(key)?.asCharacter

