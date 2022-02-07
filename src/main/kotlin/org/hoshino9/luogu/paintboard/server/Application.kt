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
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo
import java.io.File
import java.util.*

data class PaintRequest(val x: Int, val y: Int, val color: String)
data class User(val username: String, val password: String)
data class Paintboard(val name: String, val text: String)
class RequestException(errorMessage: String) : Exception(errorMessage)
object Unknown

lateinit var config: Properties
lateinit var mongo: CoroutineDatabase

val sessions: MutableList<WebSocketSession> = Collections.synchronizedList(LinkedList())

fun loadConfig() {
    config = Properties().apply {
        load(File("config.properties").inputStream())
    }
}

fun connectMongoDB() {
    val host = config.getProperty("host") ?: throw IllegalArgumentException("no host found")
    val port = config.getProperty("port") ?: "27017"
    val db = config.getProperty("database") ?: throw IllegalArgumentException("no database found")

    mongo = KMongo.createClient(host + ":" + port).coroutine.getDatabase(db)
    println("Connected to MongoDB server: $host:$port/$db")
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
    connectMongoDB()

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
            loginPage()
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