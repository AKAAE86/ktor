/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import com.fasterxml.jackson.annotation.*
import io.ktor.application.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.contentnegatiation.tests.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.common.serializaion.jackson.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlin.test.*

class ClientJacksonTest : ClientContentNegotiationTest() {
    override val converter = JacksonConverter()

    override fun createRoutes(routing: Routing): Unit = with(routing) {
        super.createRoutes(routing)

        post("/jackson") {
            assertEquals("""{"value":"request"}""", call.receive())
            call.respondText(
                """{"ok":true,"result":[{"value":"response","ignoredValue":"not_ignored"}]}""",
                ContentType.Application.Json
            )
        }
    }

    @Test
    fun testJackson() = testWithEngine(CIO) {
        configClient()

        test { client ->
            val response = client.post {
                url(port = serverPort, path = "jackson")
                setBody(Jackson("request", "ignored"))
                contentType(ContentType.Application.Json)
            }.body<Response<List<Jackson>>>()

            assertTrue(response.ok)
            val list = response.result!!
            assertEquals(1, list.size)
            assertEquals(Jackson("response", null), list[0]) // encoded with GsonConverter
        }
    }

    override fun testSealed() {}

    data class Jackson(val value: String, @JsonIgnore val ignoredValue: String?)
}
