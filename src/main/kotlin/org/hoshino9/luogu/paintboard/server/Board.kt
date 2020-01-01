package org.hoshino9.luogu.paintboard.server

import com.google.gson.Gson
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respondText
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.routing.post
import kotlinx.coroutines.launch

val board: Array<Array<Int>> = Array(800) {
    Array(400) {
        2
    }
}

var boardText: String
    get() {
        return buildString {
            board.forEach { line ->
                line.forEach {
                    append(it.toString(32).toUpperCase())
                }

                appendln()
            }
        }
    }

    set(value) {
        value.lines().filter { it.isNotBlank() }.forEachIndexed { x, line ->
            line.forEachIndexed { y, color ->
                board[x][y] = color.toString().toInt(32)
            }
        }
    }

fun Routing.board() {
    get("/paintBoard/board") {
        call.respondText(boardText)
    }

    post("/paintBoard/paint") {
        try {
            val body = call.receive<String>()
            val req = Gson().fromJson(body, PaintRequest::class.java)

            if (req.x !in 0 until 800 || req.y !in 0 until 400) throw RequestException("坐标越界")
            if (req.color !in 0..31) throw RequestException("颜色越界")

            board[req.x][req.y] = req.color
            call.respondText(
                "{\"status\":200}",
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
                HttpStatusCode.BadRequest
            )
        }
    }
}