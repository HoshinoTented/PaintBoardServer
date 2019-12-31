package org.hoshino9.luogu.paintboard.server

import com.google.gson.Gson
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.cio.websocket.*
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.websocket.WebSockets
import io.ktor.websocket.webSocket
import kotlinx.coroutines.launch
import java.util.*
import kotlin.collections.HashMap

data class PaintRequest(val x: Int, val y: Int, val color: Int)

object Unknown

class RequestException(errorMessage: String) : Exception(errorMessage)

val board: Array<Array<Int>> = Array(400) {
    Array(800) {
        2
    }
}

lateinit var whiteList: List<String>
val timer: MutableMap<String, Long> = HashMap()
val sessions: MutableList<WebSocketSession> = LinkedList()

fun loadWhiteList() {
    whiteList =
        String(Unknown::class.java.getResourceAsStream("/whitelist.txt").readBytes()).lines().filter { it.isNotBlank() }
}

@Synchronized
suspend fun onPaint(req: PaintRequest) {
    sessions.forEach {
        try {
            it.send(Gson().toJson(req))
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}

fun main() {
    loadWhiteList()
    embeddedServer(Netty, 8080) {
        install(WebSockets)

        routing {
            get("/paintBoard/board") {
                buildString {
                    board.forEach { line ->
                        line.forEach {
                            append(it.toString(32).toUpperCase())
                        }

                        appendln()
                    }
                }.let {
                    call.respondText(it, ContentType.Text.Plain)
                }
            }

            post("/paintBoard/paint") {
                try {
                    val clientId = call.request.cookies["__client_id"]
                    if (clientId != null && clientId in whiteList) {
                        val lastPaint = timer[clientId]
                        val current = System.currentTimeMillis()
                        if (lastPaint == null || current - lastPaint > 10000) {
                            val body = call.receive<String>()
                            val req = Gson().fromJson(body, PaintRequest::class.java)

                            if (req.x !in 0 until 400 || req.y !in 0 until 800) throw RequestException("position out of bounds")
                            if (req.color !in 0..31) throw RequestException("color out of bounds")

                            board[req.x][req.y] = req.color
                            timer[clientId] = current
                            call.respond(HttpStatusCode.OK)

                            launch {
                                onPaint(req)
                            }
                        } else throw RequestException("too frequently")
                    } else throw RequestException("invalid client id")
                } catch (e: Throwable) {
                    call.respondText(
                        "{\"errorType\": \"${e::class.java}\", \"errorMessage\": \"${e.message}\"}",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest
                    )
                }
            }

            webSocket("/paintBoard/ws") {
                send("Connected")

                sessions.add(this)

                for (frame in incoming) {
                    println("Received: ${String(frame.readBytes())}")
                }
            }
        }
    }.start(true)
}