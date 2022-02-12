package org.hoshino9.luogu.paintboard.server

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.post
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.runBlocking
import org.litote.kmongo.*

suspend fun PipelineContext<*, ApplicationCall>.manageRequest(block: () -> Unit) {
    val body = call.receive<String>().parseJson().asJsonObject

    val password = body["password"].asString

    if (password == config.getProperty("password")) {
        block()
        call.respond(HttpStatusCode.OK)
    } else call.respond(HttpStatusCode.Forbidden)
}

fun save() {
    runBlocking {
        mongo.getCollection<Paintboard>()
            .findOneAndUpdate(
                Paintboard::name eq "paintboard",
                setValue(Paintboard::text, board.text)
            ) ?: runBlocking {
                mongo.getCollection<Paintboard>()
                    .insertOne(Paintboard("paintboard", board.text))
            }
    }
}

fun load() {
    runBlocking {
        board.text = mongo.getCollection<Paintboard>()
            .findOne(Paintboard::name eq "paintboard")?.text
            ?: throw IllegalStateException("No such document: paintboard")
    }
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