package org.hoshino9.luogu.paintboard.server

import com.google.gson.Gson
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import org.litote.kmongo.eq
import org.litote.kmongo.newId
import java.security.MessageDigest

fun toHexString(byteArray: ByteArray) = with(StringBuilder()) {
    byteArray.forEach {
        val hex = it.toInt() and (0xFF)
        val hexStr = Integer.toHexString(hex)
        if (hexStr.length == 1) append("0").append(hexStr)
        else append(hexStr)
    }
    toString()
}

fun getSalt(length: Int) = StringBuilder()
    .apply { (1..length).onEach { append("0123456789abcdef".random()) } }.toString()

fun encrypt(s: String, salt: String = getSalt(16)) = with(StringBuilder()) {
    val md5 = toHexString(MessageDigest.getInstance("MD5").digest((s + salt).toByteArray()))
    println(md5)
    println(salt)
    (0 until 16).onEach { i -> append(salt[i]).append(md5[i * 2]).append(md5[i * 2 + 1]) }

    toString()
}

suspend fun userAuth(user: User): User? {
    val query = mongo.getCollection<User>()
        .findOne(User::username eq user.username) ?: return null
    val salt = StringBuilder(16).apply {
        (0 until 48 step 3).onEach { i -> append(query.password[i]) }
    }

    return if (query.password == encrypt(user.password, salt.toString())) query else null
}

fun Routing.loginPage() {
    post("/paintBoard/login") {
        try {
            val body = call.receive<String>()
            val req = Gson().fromJson(body, User::class.java)
            val user = userAuth(req)
            val session = call.sessions.get<UserSession>()

            if (session != null) {
                call.respondText(
                    "{\"status\": 200,\"data\": {\"username\":\"${session.username}\"}}",
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.OK
                )
            } else if (user != null) {
                call.sessions.set(UserSession(user._id.toString(), user.username,
                    System.currentTimeMillis() - delay))
                call.respondText(
                    "{\"status\": 200,\"data\": {\"username\":\"${user.username}\"}}",
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.OK
                )
            } else call.respondText(
                "{\"status\": 200,\"data\": \"用户名或密码错误\"}",
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.OK
            )
        } catch (e: Throwable) {
            call.respondText(
                "{\"status\": 400,\"data\": \"${e.message}\"}",
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.BadRequest
            )
        }
    }

    post("/paintBoard/user") {
        try {
            val body = call.receive<String>()
            val req = Gson().fromJson(body, User::class.java)

            mongo.getCollection<User>().insertOne(User(newId(), req.username, encrypt(req.password)))

            call.respondText(
                "{\"status\":200}",
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.OK
            )
        } catch (e: Throwable) {
            call.respondText(
                "{\"status\": 400,\"data\": \"${e.message}\"}",
                contentType =ContentType.Application.Json,
                status = HttpStatusCode.BadRequest
            )
        }
    }
}