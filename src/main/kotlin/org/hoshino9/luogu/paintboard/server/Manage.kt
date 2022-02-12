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
import java.util.*

suspend fun PipelineContext<*, ApplicationCall>.manageRequest(block: () -> Unit) {
    val body = call.receive<String>().parseJson().asJsonObject

    val password = body["password"].asString

    if (password == config.getProperty("password")) {
        block()
        call.respond(HttpStatusCode.OK)
    } else call.respond(HttpStatusCode.Forbidden)
}

suspend fun saveAll() {
    for (id in boards.indices) {
        save(id)
    }
}

suspend fun save(id: Int) {
    println("Saving board $id...")
    val record = PaintboardRecord(Date(), 800, 400, boards[id].text)
    mongo.getCollection<PaintboardRecord>("paintboard$id").insertOne(record)
}

suspend fun loadAll() {
    for (id in boards.indices) {
        load(id)
    }
}

suspend fun load(id: Int) {

    val record = mongo.getCollection<PaintboardRecord>("paintboard$id")
        .find().descendingSort(PaintboardRecord::date).first()
    if (record == null) {
        save(id)
        mongo.getCollection<PaintboardRecord>("paintboard$id")
            .createIndex("{date: 1}")
    } else {
        boards[id].text = record.text
    }
}

suspend fun rollback(id: Int, date: Date) {
    mongo.getCollection<PaintboardRecord>("paintboard$id")
        .deleteMany(
            PaintboardRecord::date gt date
        )
    load(id)
}

fun Routing.managePage() {
    post("/paintBoard/save") {
        manageRequest {
            launch {
                saveAll()
            }
        }
    }

    post("/paintBoard/load") {
        manageRequest {
            launch {
                loadAll()
            }
        }
    }
}