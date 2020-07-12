package org.traccar.gateway

import android.util.JsonReader
import fi.iki.elonen.NanoHTTPD
import java.io.InputStreamReader

class WebServer(port: Int, private val handler: Handler) : NanoHTTPD(port) {

    interface Handler {
        fun onSendMessage(phone: String, message: String): String?
    }

    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.POST) {
            var phone: String? = null
            var message: String? = null
            val reader = JsonReader(InputStreamReader(session.inputStream))
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "to"      -> phone = reader.nextString()
                    "message" -> message = reader.nextString()
                }
            }
            val result = if (phone != null && message != null) {
                handler.onSendMessage(phone, message)
            } else {
                "Missing phone or message"
            }
            return newFixedLengthResponse(
                if (result == null) Response.Status.OK else Response.Status.INTERNAL_ERROR,
                MIME_HTML,
                result
            )
        } else {
            return newFixedLengthResponse(
                """
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body>
                    <p>Send SMS using following API:</p>
                    <pre>
                    POST /
                    {
                        "to": "+10000000000",
                        "message": "Your message"
                    }
                    </pre>
                </body>
                </html>
                """.trimIndent()
            )
        }
    }

}
