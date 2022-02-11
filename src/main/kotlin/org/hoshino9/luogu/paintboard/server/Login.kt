package org.hoshino9.luogu.paintboard.server

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.ktor.application.*
import io.ktor.client.utils.EmptyContent.status
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.util.pipeline.*
import org.litote.kmongo.eq
import org.litote.kmongo.newId
import org.litote.kmongo.or
import java.security.MessageDigest

suspend fun PipelineContext<Unit, ApplicationCall>.catchAndRespond(
    block: suspend PipelineContext<Unit, ApplicationCall>.() -> Unit
) {
    try {
        block()
    } catch (e: Throwable) {
        call.respondText(
            "{\"status\": 400,\"data\": \"${e.message}\"}",
            contentType = ContentType.Application.Json,
            status = HttpStatusCode.BadRequest
        )
    }
}

fun toHexString(byteArray: ByteArray) = with(StringBuilder()) {
    byteArray.forEach {
        val hex = it.toInt() and (0xFF)
        val hexStr = Integer.toHexString(hex)
        if (hexStr.length == 1) append("0").append(hexStr)
        else append(hexStr)
    }
    toString()
}

fun getSalt(length: Int, pattern: String = "0123456789abcdef") = StringBuilder()
    .apply { (1..length).onEach { append(pattern.random()) } }.toString()

fun encrypt(s: String, salt: String = getSalt(16)) = with(StringBuilder()) {
    val md5 = toHexString(MessageDigest.getInstance("MD5").digest((s + salt).toByteArray()))
    (0 until 16).onEach { i -> append(salt[i]).append(md5[i * 2]).append(md5[i * 2 + 1]) }
    toString()
}

suspend fun userAuth(user: User): User? {
    val query = mongo.getCollection<User>()
        .findOne(or(User::username eq user.username, User::email eq user.email)) ?: return null
    val salt = StringBuilder(16).apply { (0 until 48 step 3).onEach { i -> append(query.password[i]) } }

    return if (query.password == encrypt(user.password, salt.toString())) query else null
}

suspend fun sendCaptcha(email: String, captcha: String) {
    println(captcha) // TODO send captcha with email
}

fun Routing.loginPage() {
    post("/paintBoard/login") {
        catchAndRespond {
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
                "{\"status\": 403,\"data\": \"用户名或密码错误\"}",
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.OK
            )
        }
    }

    get("/paintBoard/logout") {
        catchAndRespond {
            call.sessions.clear<UserSession>()
            call.respondRedirect("/paintBoard")
        }
    }

    get("/paintBoard/captcha") {
        catchAndRespond {
            val email = call.parameters["email"] ?: throw RequestException("请输入邮箱")
            val capt = getSalt(6,"0123456789")
            val query = mongo.getCollection<User>().findOne(User::email eq email)

            if (query != null) { throw RequestException("该邮箱已被注册") }
            sendCaptcha(email, capt)
            call.sessions.set<RegisterSession>(RegisterSession(email, capt, System.currentTimeMillis()))
            call.respond(HttpStatusCode.OK)
        }
    }

    get("/paintBoard/user") {
        catchAndRespond {
            val user = mongo.getCollection<User>().findOne(
                or(User::username eq call.parameters["username"], User::email eq call.parameters["email"])
            ) ?: throw RequestException("未找到该用户")

            call.respondText(
                "{\"status\": 200,\"data\": {\"username\":\"${user.username}\",\"email\":\"${user.email}\"}}",
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.OK
            )
        }
    }

    post("/paintBoard/user") {
        catchAndRespond {
            val body = call.receive<String>()
            val req = Gson().fromJson(body, User::class.java)
            val capt = Gson().fromJson(body, JsonObject::class.java).get("captcha").getAsString()
            val session = call.sessions.get<RegisterSession>() ?: throw RequestException("请先获取验证码")
            val query = mongo.getCollection<User>()
                .findOne(or(User::username eq req.username, User::email eq req.email))

            if (query != null) { throw RequestException("该用户名或邮箱已被注册") }
            if (capt != session.captcha || req.email != session.email ||
                System.currentTimeMillis() - session.time > 5 * 60 * 1000) throw RequestException("验证码无效")

            mongo.getCollection<User>().insertOne(User(newId(), req.username, req.email, encrypt(req.password)))

            call.sessions.clear<RegisterSession>()
            call.respondText(
                "{\"status\":200}",
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.OK
            )
        }
    }
}