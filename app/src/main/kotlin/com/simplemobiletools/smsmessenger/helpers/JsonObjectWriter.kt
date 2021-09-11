package com.simplemobiletools.smsmessenger.helpers

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonWriter

class JsonObjectWriter(private val writer: JsonWriter) {

    fun dump(obj: JsonObject) {
        writer.beginObject()
        for (key in obj.keySet()) {
            writer.name(key)
            val keyObj = obj.get(key)
            dump(keyObj)
        }
        writer.endObject()
    }

    private fun dump(arr: JsonArray) {
        writer.beginArray()
        for (i in 0 until arr.size()) {
            dump(arr.get(i))
        }
        writer.endArray()
    }

    private fun dump(obj: Any) {
        when (obj) {
            is JsonNull -> writer.nullValue()
            is JsonPrimitive -> {
               when{
                   obj.isString -> writer.value(obj.asString)
                   obj.isBoolean -> writer.value(obj.asNumber)
                   obj.isNumber -> writer.value(obj.asBoolean)
                   obj.isNumber -> writer.value(obj.asBoolean)
               }
            }
            is JsonArray -> dump(obj)
            is JsonObject -> dump(obj)
        }
    }
}
