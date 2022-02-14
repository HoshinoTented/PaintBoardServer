package org.hoshino9.luogu.paintboard.server

import com.google.gson.Gson
import com.google.gson.JsonElement
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.sessions.*
import io.ktor.util.*
import io.ktor.websocket.*
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.litote.kmongo.Id
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo
import java.io.File
import java.util.*

data class PaintRequest(val x: Int, val y: Int, val color: String)
data class User(val _id: Id<User>?, val username: String, val email: String, val password: String)
data class UserSession(val id: String, val username: String, val time: Long) : Principal
data class RegisterSession(val email: String, val captcha: String, val time: Long) : Principal
data class PaintRecord(
    val time: Long,
    val user: String,
    val x: Int,
    val y: Int,
    val color: Int
)

class RequestException(errorMessage: String) : Exception(errorMessage)
object Unknown

lateinit var config: Properties
lateinit var mongo: CoroutineDatabase

var delay: Long = 0

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

    mongo = KMongo.createClient("$host:$port").coroutine.getDatabase(db)
    println("Connected to MongoDB server: $host:$port/$db")
}

suspend fun onPaint(req: PaintRequest, id: Int) {
    val str = "{\"type\":\"paintboard_update\",\"id\":$id}"
    sessions.forEach {
        try {
            it.send(str)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}

suspend fun onRefresh(id: Int) {
    val str = "{\"type\":\"refresh\",\"id\":$id}"
    sessions.forEach {
        try {
            it.send(str)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}

fun main() {
    loadConfig()
    connectMongoDB()

    delay = (config.getProperty("delay")?.toLong() ?: 0) * 1000

    runBlocking {
        initDB()
        loadAllBoards(System.currentTimeMillis())
    }

    embeddedServer(Netty, 8080) {
        install(Compression)
        install(WebSockets)
        install(ContentNegotiation) {
            gson {
            }
        }
        install(Sessions) {
            cookie<UserSession>("user_session") {
                cookie.path = "/"
                directorySessionStorage(File(".sessions"), cached = true)
            }
            cookie<RegisterSession>("register_session") {
                cookie.path = "/"
                transform(
                    SessionTransportTransformerEncrypt(
                        hex(config.getProperty("encryptkey")),
                        hex(config.getProperty("signkey"))
                    )
                )
            }
        }
        install(Authentication) {
            session<UserSession>("auth-session") {
                validate { session ->
                    session
                }
                challenge {
                    call.respondText(
                        "{\"status\": 400, \"data\": \"请先登录\"}",
                        contentType = ContentType.Application.Json,
                        status = HttpStatusCode.OK
                    )
                }
            }
        }

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