package org.hoshino9.luogu.paintboard.server

import com.google.gson.Gson
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.sessions.*
import kotlinx.coroutines.launch

class Board {
    private val _pixel = Array(800) { IntArray(600) }
    private var _text: String? = null
    operator fun get(x: Int, y: Int): Int {
        return _pixel[x][y]
    }

    operator fun set(x: Int, y: Int, value: Int) {
        _pixel[x][y] = value
        _text = null
    }

    var text: String
        get() {
            val txt = _text
            if (txt != null) {
                return txt
            }
            val newTxt =
                _pixel.joinToString(separator = ";") { line -> line.joinToString(separator = ",") { "%06x".format(it) } }
            _text = newTxt
            return newTxt
        }
        set(value) {
            _text = value
            value.split(';').forEachIndexed { x, line ->
                line.split(',').forEachIndexed { y, color ->
                    _pixel[x][y] = color.toInt(16)
                }
            }
        }
}

val board = Board()

fun Routing.board() {
    get("/paintBoard/board") {
        call.respondText(board.text)
    }

    authenticate("auth-session") {
        post("/paintBoard/paint") {
            try {
                val body = call.receive<String>()
                val req = Gson().fromJson(body, PaintRequest::class.java)
                val session = call.principal<UserSession>()

                if (System.currentTimeMillis() - (session?.time
                        ?: 0) <= delay
                ) throw RequestException("冷却时间未到，暂时不能涂色")
                if (req.x !in 0 until 800 || req.y !in 0 until 400) throw RequestException("坐标越界")
                if (req.color.toInt(16) !in 0x000000..0xFFFFFF) throw RequestException("颜色越界")

                board[req.x, req.y] = req.color.toInt(16)
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