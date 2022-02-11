package org.hoshino9.luogu.paintboard.server

import com.google.gson.Gson
import io.ktor.application.call
import io.ktor.auth.*
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respondText
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.sessions.*
import kotlinx.coroutines.launch
import java.util.*

val board: MutableList<MutableList<Int>> = Collections.synchronizedList(
    ArrayList<MutableList<Int>>().apply {
        repeat(800) {
            ArrayList<Int>().apply {
                repeat(400) {
                    add(0x000000)
                }
            }.let {
                Collections.synchronizedList(it).run(::add)
            }
        }
    }
)

var boardText: String
    get() {
        return buildString {
            board.forEach { line ->
                line.joinToString(separator = "|") { "%06X".format(it) }.run(::appendLine)
            }
        }
    }

    set(value) {
        value.lines().filter { it.isNotBlank() }.forEachIndexed { x, line ->
            line.split('|').forEachIndexed { y, color ->
                board[x][y] = color.toInt(16)
            }
        }
    }

fun Routing.board() {
    get("/paintBoard/board") {
        call.respondText(boardText)
    }

    authenticate("auth-session"){
        post("/paintBoard/paint") {
            try {
                val body = call.receive<String>()
                val req = Gson().fromJson(body, PaintRequest::class.java)
                val session = call.principal<UserSession>()

                if (System.currentTimeMillis() - (session?.time ?: 0) <= delay) throw RequestException("冷却时间未到，暂时不能涂色")
                if (req.x !in 0 until 800 || req.y !in 0 until 400) throw RequestException("坐标越界")
                if (req.color.toInt(16) !in 0x000000..0xFFFFFF) throw RequestException("颜色越界")

                board[req.x][req.y] = req.color.toInt(16)
                call.sessions.set(session?.copy(time = System.currentTimeMillis()))
                call.respondText(
                    "{\"status\": 200}",
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.OK
                )

                launch {
                    onPaint(req)
                }
            } catch (e: Throwable) {
                call.respondText(
                    "{\"status\": 400,\"data\": \"${e.message}\"}",
                    ContentType.Application.Json,
                    HttpStatusCode.OK
                )
            }
        }
    }
}