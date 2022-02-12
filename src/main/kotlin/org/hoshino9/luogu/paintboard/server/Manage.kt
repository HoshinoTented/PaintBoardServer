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
import java.util.*

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
        println("Saving boards...")
        for((id, board) in boards.withIndex()) {
            val record = PaintboardRecord(Date(), 800, 400, board.text)
            mongo.getCollection<PaintboardRecord>("paintboard$id").insertOne(record)
        }
    }
}

fun load() {
    runBlocking {
        for((id, board) in boards.withIndex()) {
            val record = mongo.getCollection<PaintboardRecord>("paintboard$id")
                .find().descendingSort(PaintboardRecord::date).first()
            if (record == null) {
                save()
            } else {
                board.text = record.text
            }
        }
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