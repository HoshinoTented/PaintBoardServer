package org.hoshino9.luogu.paintboard.server

import com.google.gson.JsonParser
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.post
import io.ktor.util.pipeline.PipelineContext
import java.io.File

suspend fun PipelineContext<*, ApplicationCall>.manageRequest(block: () -> Unit) {
    val body = call.receive<String>().parseJson().asJsonObject

    val password = body["password"].asString

    if (password == config.getProperty("password")) {
        block()
        call.respond(HttpStatusCode.OK)
    } else call.respond(HttpStatusCode.Forbidden)
}

fun save() {
    redis["board"] = boardText
}

fun load() {
    boardText = redis["board"] ?: throw IllegalStateException("No such field: board")
}

fun Routing.managePage() {
    post("/paintBoard/save") {
        manageRequest {
            save()
        }
    }

    post("/paintBoard/load") {
        manageRequest {
            load()
        }
    }
}