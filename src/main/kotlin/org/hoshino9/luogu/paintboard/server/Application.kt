package org.hoshino9.luogu.paintboard.server

import com.google.gson.Gson
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.http.ContentType
import io.ktor.http.cio.websocket.WebSocketSession
import io.ktor.http.cio.websocket.readBytes
import io.ktor.http.cio.websocket.send
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.websocket.WebSockets
import io.ktor.websocket.webSocket
import java.util.*

data class PaintRequest(val x: Int, val y: Int, val color: Int)
class RequestException(errorMessage: String) : Exception(errorMessage)
object Unknown

lateinit var config: Properties
val sessions: MutableList<WebSocketSession> = LinkedList()

fun loadConfig() {
    config = Properties().apply {
        load(Unknown::class.java.getResourceAsStream("/config.properties"))
    }
}

suspend fun onPaint(req: PaintRequest) {
    sessions.forEach {
        try {
            it.send(Gson().toJsonTree(req).apply { asJsonObject.addProperty("type", "paintboard_update") }.toString())
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}

fun main() {
    loadConfig()
    embeddedServer(Netty, 8080) {
        install(WebSockets)

        routing {
            managePage()
            board()

            get("/paintBoard") {
                call.respondText(
                    String(Unknown::class.java.getResourceAsStream("/paintboard.html").readBytes()),
                    ContentType.Text.Html
                )
            }

            webSocket("/paintBoard/ws") {
                try {
                    send("{\"type\": \"result\"}")
                    sessions.add(this)

                    for (frame in incoming) {
                        println("Received: ${String(frame.readBytes())}")
                    }
                } finally {
                    sessions.remove(this)
                    println("Removed.")
                }
            }
        }
    }.start(true)
}