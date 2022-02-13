package org.hoshino9.luogu.paintboard.server

import com.google.gson.Gson
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.*
import io.ktor.request.receive
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.*

import org.litote.kmongo.*
import java.awt.Paint
import java.util.*

suspend fun PipelineContext<*, ApplicationCall>.manageRequest(block: suspend PipelineContext<*, ApplicationCall>. () -> Unit) {
    val body = call.receive<String>().parseJson().asJsonObject

    val password = body["password"].asString

    if (password == config.getProperty("password")) {
        block()
        call.respond(HttpStatusCode.OK)
    } else call.respond(HttpStatusCode.Forbidden)
}

fun Routing.managePage() {
    get("/paintBoard/history") {
        manageRequest {
            try {
                val id = call.request.queryParameters["id"]?.toInt() ?: throw RequestException("未指定画板号")
                val time = call.request.queryParameters["time"]?.toLong() ?: throw RequestException("未指定时间")
                val board = readBoard(id, time)
                call.respondText(board.toString())
            } catch (e: Throwable) {
                call.respondText(
                    "{\"status\": 400,\"data\": \"${e.message}\"}",
                    ContentType.Application.Json,
                    HttpStatusCode.OK
                )
            }
        }
    }

    get("/paintBoard/blame") {
        manageRequest {
            try {
                val id = call.request.queryParameters["id"]?.toInt() ?: throw RequestException("未指定画板号")
                val time = call.request.queryParameters["time"]?.toLong() ?: throw RequestException("未指定时间")
                val x = call.request.queryParameters["x"]?.toInt() ?: throw RequestException("未指定坐标")
                val y = call.request.queryParameters["y"]?.toInt() ?: throw RequestException("未指定坐标")
                val record = blame(id, time, x, y)
                call.respondText(Gson().toJsonTree(record).toString())
            } catch (e: Throwable) {
                call.respondText(
                    "{\"status\": 400,\"data\": \"${e.message}\"}",
                    ContentType.Application.Json,
                    HttpStatusCode.OK
                )
            }
        }
    }

    post("/paintBoard/rollback") {
        manageRequest {
            try {
                val id = call.request.queryParameters["id"]?.toInt() ?: throw RequestException("未指定画板号")
                val time = call.request.queryParameters["time"]?.toLong() ?: throw RequestException("未指定时间")
                rollback(id, time)
                call.respondText("{\"status\": 200}")
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