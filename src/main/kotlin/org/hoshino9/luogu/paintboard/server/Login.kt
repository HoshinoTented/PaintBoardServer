package org.hoshino9.luogu.paintboard.server

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*

fun Routing.loginPage() {
    get("/paintBoard/login") {
        call.respondText(
            "Hello, world",
            status = HttpStatusCode.OK
        )
    }
}