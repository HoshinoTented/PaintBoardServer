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
import kotlinx.coroutines.runBlocking
import org.litote.kmongo.*
import java.awt.Paint
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.*
import java.util.*

class Board() {
    private val data = Array(800) { IntArray(400) }

    constructor(initImage: BufferedImage?, records: List<PaintRecord>) : this() {
        if (initImage != null) {
            if (initImage.width < 800 || initImage.height < 400) throw Exception("The image is too small")
            for (x in 0..800 - 1) {
                for (y in 0..400 - 1) {
                    data[x][y] = initImage.getRGB(x, y) and 0xffffff
                }
            }
        }
        for (record in records) {
            data[record.x][record.y] = record.color
        }
    }

    operator fun get(x: Int, y: Int): Int {
        return data[x][y]
    }

    operator fun set(x: Int, y: Int, value: Int) {
        data[x][y] = value
    }

    override fun toString(): String {
        return data.joinToString(separator = ";") { line -> line.joinToString(separator = ",") { "%06x".format(it) } }
    }
}

val boardNum = config.getProperty("boardNum").toInt()
val boards = Array(boardNum) { Board() }
val stringCache = Array<String?>(boardNum) { null }

val initImages = Array(boardNum) { id -> tryReadImage("initImage/$id.jpg") }

fun tryReadImage(path: String): BufferedImage? {
    try {
        return ImageIO.read(File(path))
    } catch (e: Throwable) {
        return null
    }
}

suspend fun initDB() {
    for (id in 0..boardNum - 1) {
        mongo.getCollection<PaintRecord>("paintboard$id")
            .createIndex("{time:1}")
    }
}

suspend fun loadAllBoards(time: Long) {
    for (id in 0..boardNum - 1) {
        loadBoard(id, time)
    }
}

suspend fun loadBoard(id: Int, time: Long) {
    boards[id] = readBoard(id, time)
    stringCache[id] = null
}

suspend fun readBoard(id: Int, time: Long): Board {
    val recordList = mongo.getCollection<PaintRecord>("paintboard$id")
        .find(PaintRecord::time lte time)
        .toList()
    return Board(initImages[id], recordList)
}

suspend fun rollback(id: Int, time: Long) {
    mongo.getCollection<PaintRecord>("paintboard$id")
        .deleteMany(PaintRecord::time gt time)
    loadBoard(id, System.currentTimeMillis())
}

suspend fun blame(id: Int, time: Long, x: Int, y: Int): PaintRecord? {
    return mongo.getCollection<PaintRecord>("paintboard$id")
        .find(PaintRecord::time lte time, PaintRecord::x eq x, PaintRecord::y eq y)
        .descendingSort().first()
}

fun Routing.board() {
    get("/paintBoard/board") {
        try {
            val id = call.request.queryParameters["id"]?.toInt() ?: throw RequestException("未指定画板号")
            val str = stringCache[id]
            if (str != null) {
                call.respondText(str)
            } else {
                val newStr = boards[id].toString()
                stringCache[id] = newStr
                call.respondText(newStr)
            }
        } catch (e: Throwable) {
            call.respondText(
                "{\"status\": 400,\"data\": \"${e.message}\"}",
                ContentType.Application.Json,
                HttpStatusCode.OK
            )
        }
    }

    authenticate("auth-session") {
        post("/paintBoard/paint") {
            try {
                val id = call.request.queryParameters["id"]?.toInt() ?: throw RequestException("未指定画板号")
                val body = call.receive<String>()
                val req = Gson().fromJson(body, PaintRequest::class.java)
                val session = call.principal<UserSession>()

                if (System.currentTimeMillis() - (session?.time
                        ?: 0) <= delay
                ) throw RequestException("冷却时间未到，暂时不能涂色")
                if (req.x !in 0 until 800 || req.y !in 0 until 400) throw RequestException("坐标越界")
                if (req.color.toInt(16) !in 0x000000..0xFFFFFF) throw RequestException("颜色越界")

                boards[id][req.x, req.y] = req.color.toInt(16)
                stringCache[id] = null

                call.sessions.set(session?.copy(time = System.currentTimeMillis()))
                call.respondText(
                    "{\"status\": 200}",
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.OK
                )

                launch {
                    onPaint(req, id)
                }

                launch {
                    mongo.getCollection<PaintRecord>("paintboard$id")
                        .insertOne(
                            PaintRecord(
                                System.currentTimeMillis(),
                                call.authentication.principal<UserSession>()?.username ?: "not login?",
                                req.x, req.y, req.color.toInt(16)
                            )
                        )
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