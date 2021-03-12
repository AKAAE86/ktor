/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.common.jackson

import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.module.kotlin.*
import io.ktor.common.serialization.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import kotlin.reflect.*
import kotlin.reflect.jvm.*
import kotlin.text.Charsets

public class JacksonConverter(private val objectmapper: ObjectMapper = jacksonObjectMapper()) : ContentConverter {

    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        type: KType?,
        value: Any
    ): OutgoingContent {
        return OutputStreamContent(
            {
                if (charset == Charsets.UTF_8) {
                    /*
                    Jackson internally does special casing on UTF-8, presumably for performance reasons. Thus we pass an
                    InputStream instead of a writer to let Jackson do it's thing.
                     */
                    objectmapper.writeValue(this, value)
                } else {
                    objectmapper.writeValue(this.writer(charset = charset), value)
                }
            },
            contentType.withCharset(charset)
        )
    }

    override suspend fun deserialize(charset: Charset, type: KType, content: ByteReadChannel): Any? {
        return withContext(Dispatchers.IO) {
            val reader = content.toInputStream().reader(charset)
            objectmapper.readValue(reader, type.jvmErasure.javaObjectType)
        }
    }
}
