/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.gson

import com.google.gson.*
import io.ktor.common.serialization.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.reflect.*
import kotlin.reflect.jvm.*

/**
 * GSON converter for [ContentNegotiation] feature
 */
public class GsonConverter(private val gson: Gson = Gson()) : ContentConverter {

    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        type: KType?,
        value: Any
    ): OutgoingContent? {
        return TextContent(gson.toJson(value), contentType.withCharset(charset))
    }

    override suspend fun deserialize(charset: Charset, type: KType, content: ByteReadChannel): Any? {
        val javaType = type.jvmErasure

        if (gson.isExcluded(javaType)) {
            throw ExcludedTypeGsonException(javaType)
        }

        return withContext(Dispatchers.IO) {
            val reader = content.toInputStream().reader(charset)
            gson.fromJson(reader, type.javaType)
        }
    }
}

private fun Gson.isExcluded(type: KClass<*>) =
    excluder().excludeClass(type.java, false)

@OptIn(ExperimentalCoroutinesApi::class)
internal class ExcludedTypeGsonException(
    private val type: KClass<*>
) : Exception("Type ${type.jvmName} is excluded so couldn't be used in receive"),
    CopyableThrowable<ExcludedTypeGsonException> {

    override fun createCopy(): ExcludedTypeGsonException = ExcludedTypeGsonException(type).also {
        it.initCause(this)
    }
}
