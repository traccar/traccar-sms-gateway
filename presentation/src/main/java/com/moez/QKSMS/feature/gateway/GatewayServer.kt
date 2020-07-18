package com.moez.QKSMS.feature.gateway

import android.util.JsonReader
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.AbstractHandler
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class GatewayServer(port: Int, private val handler: Handler) : Server(port) {

    interface Handler {
        fun onSendMessage(phone: String, message: String): String?
    }

    init {
        setHandler(object : AbstractHandler() {
            override fun handle(
                target: String,
                baseRequest: Request,
                request: HttpServletRequest,
                response: HttpServletResponse
            ) {
                response.contentType = "text/html; charset=utf-8"

                if (request.method == "POST") {
                    handlePost(request, response)
                } else {
                    handleGet(response)
                }

                baseRequest.isHandled = true
            }
        })
    }

    private fun handlePost(
        request: HttpServletRequest,
        response: HttpServletResponse
    ) {
        var phone: String? = null
        var message: String? = null

        val reader = JsonReader(request.reader)
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

        if (result == null) {
            response.status = HttpServletResponse.SC_OK
        } else {
            response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            response.writer.print(result)
        }
    }

    private fun handleGet(
        response: HttpServletResponse
    ) {
        response.writer.print(
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
