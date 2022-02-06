package org.hoshino9.luogu.paintboard.server

import com.google.gson.JsonElement
import com.google.gson.JsonParser

fun String.parseJson(): JsonElement {
    return JsonParser.parseString(this)
}