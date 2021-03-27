/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.common.serialization.*
import io.ktor.http.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.*
import kotlin.test.*

@Serializable
internal data class User(val id: Long, val login: String)

@Serializable
internal data class Photo(val id: Long, val path: String)

@Serializable
data class GithubProfile(
    val login: String,
    val id: Int,
    val name: String
)

class KotlinxSerializerTest : ClientLoader() {

    @Test
    fun testCustomFormBody() = clientTests {
        config {
            install(ContentNegotiation) {
                json()
            }
        }

        val data = {
            formData {
                append("name", "hello")
                append("content") {
                    writeText("123456789")
                }
                append("file", "urlencoded_name.jpg") {
                    for (i in 1..4096) {
                        writeByte(i.toByte())
                    }
                }
                append("hello", 5)
            }
        }

        test { client ->
            var throwed = false
            try {
                client.submitFormWithBinaryData(url = "upload", formData = data()).bodyAsText()
            } catch (cause: Throwable) {
                throwed = true
            }

            assertTrue(throwed, "Connection exception expected.")
        }
    }

    @Test
    fun testStringWithJsonFeature() = clientTests {
        config {
            install(ContentNegotiation) {
                json()
            }
            defaultRequest {
                val contentType = ContentType.parse("application/vnd.string+json")
                accept(contentType)
                contentType(contentType)
            }
        }

        test { client ->
            val response = client.post("$TEST_SERVER/echo-with-content-type") {
                setBody("Hello")
            }.bodyAsText()
            assertEquals("\"Hello\"", response)

            val textResponse = client.post("$TEST_SERVER/echo") {
                setBody("Hello")
            }.bodyAsText()
            assertEquals("\"Hello\"", textResponse)

            val emptyResponse = client.post("$TEST_SERVER/echo").bodyAsText()
            assertEquals("", emptyResponse)
        }
    }

    @Test
    fun testMultipleListSerializersWithClient() = clientTests {
        config {
            install(ContentNegotiation) {
                json()
            }
            defaultRequest {
                accept(ContentType.Application.Json)
            }
        }

        test { client ->
            val users = client.get("$TEST_SERVER/json/users").body<List<User>>()
            val photos = client.get("$TEST_SERVER/json/photos").body<List<Photo>>()

            assertEquals(listOf(User(42, "TestLogin")), users)
            assertEquals(listOf(Photo(4242, "cat.jpg")), photos)
        }
    }
}
