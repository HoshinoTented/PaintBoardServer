package org.hoshino9.luogu.paintboard.server

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.post
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.*

import org.litote.kmongo.*
import java.awt.Paint
import java.util.*

suspend fun PipelineContext<*, ApplicationCall>.manageRequest(block: () -> Unit) {
    val body = call.receive<String>().parseJson().asJsonObject

    val password = body["password"].asString

    if (password == config.getProperty("password")) {
        block()
        call.respond(HttpStatusCode.OK)
    } else call.respond(HttpStatusCode.Forbidden)
}

fun Routing.managePage() {
    post("/paintBoard/save") {
         manageRequest{

        }
    }

    post("/paintBoard/load") {
        manageRequest {

        }
    }

}