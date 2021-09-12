package com.simplemobiletools.smsmessenger.helpers

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonWriter

class JsonObjectWriter(private val writer: JsonWriter) {

    fun write(obj: JsonObject) {
        writer.beginObject()
        for (key in obj.keySet()) {
            writer.name(key)
            val keyObj = obj.get(key)
            write(keyObj)
        }
        writer.endObject()
    }

    private fun write(arr: JsonArray) {
        writer.beginArray()
        for (i in 0 until arr.size()) {
            write(arr.get(i))
        }
        writer.endArray()
    }

    private fun write(obj: Any) {
        when (obj) {
            is JsonNull -> writer.nullValue()
            is JsonPrimitive -> {
               when{
                   obj.isString -> writer.value(obj.asString)
                   obj.isBoolean -> writer.value(obj.asBoolean)
                   obj.isNumber -> writer.value(obj.asNumber)
               }
            }
            is JsonArray -> write(obj)
            is JsonObject -> write(obj)
        }
    }
}
