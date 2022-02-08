package org.hoshino9.luogu.paintboard.server

import com.google.gson.Gson
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import org.litote.kmongo.eq
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

suspend fun userCert(user: User): Boolean {
    val query = mongo.getCollection<User>()
        .findOne(User::username eq user.username) ?: return false
    val salt = StringBuilder(16)

    (0 until 48 step 3).onEach { i -> salt.append(query.password[i]) }

    return query.password == encrypt(user.password, salt.toString())
}

fun Routing.loginPage() {
    post("/paintBoard/login") {
        val body = call.receive<String>()
        val req = Gson().fromJson(body, User::class.java)

        if (userCert(req)) call.respondText(
            Gson().toJson(req),
            contentType  = ContentType.Application.Json,
            status = HttpStatusCode.OK
        )
        else call.respondText(
            "{\"status\":200}",
            contentType  = ContentType.Application.Json,
            status = HttpStatusCode.OK
        )
    }

    post("/paintBoard/user") {
        val body = call.receive<String>()
        val req = Gson().fromJson(body,User::class.java)

        mongo.getCollection<User>().insertOne(User(req.username, encrypt(req.password)))
        call.respondText(
            "{\"status\":200}",
            contentType = ContentType.Application.Json,
            status = HttpStatusCode.OK
        )
    }
}