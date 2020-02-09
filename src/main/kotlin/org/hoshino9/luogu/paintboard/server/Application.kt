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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import redis.clients.jedis.Jedis
import java.io.File
import java.util.*

data class PaintRequest(val x: Int, val y: Int, val color: String)
class RequestException(errorMessage: String) : Exception(errorMessage)
object Unknown

lateinit var config: Properties
lateinit var redis: Jedis

val sessions: MutableList<WebSocketSession> = Collections.synchronizedList(LinkedList())

fun loadConfig() {
    config = Properties().apply {
        load(File("config.properties").inputStream())
    }
}

fun connectRedis() {
    val host = config.getProperty("host") ?: throw IllegalArgumentException("no host found")
    val port = config.getProperty("port")?.toInt() ?: 6379

    redis = Jedis(host, port)
    println("Connected redis server: $host:$port")
}

suspend fun onPaint(req: PaintRequest) {
    sessions.forEach {
        try {
            it.send(Gson().toJsonTree(req).apply {
                asJsonObject.addProperty("type", "paintboard_update")
            }.toString())
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}

fun main() {
    loadConfig()
    connectRedis()

    try {
        load()
    } catch (e: Exception) {
        e.printStackTrace()
    }

    embeddedServer(Netty, 8080) {
        launch {
            while (true) {
                println("Saving board...")
                save()
                delay(5 * 60 * 1000)
            }
        }

        install(WebSockets)

        routing {
            managePage()
            board()

            get("/paintBoard") {
                var html = String(Unknown::class.java.getResourceAsStream("/paintboard.html").readBytes())
                if (config.containsKey("wsurl")) {
                    html = html.replace("\${wsurl}", config.getProperty("wsurl"))
                }

                call.respondText(html, ContentType.Text.Html)
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