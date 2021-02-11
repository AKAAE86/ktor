/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.features

import io.ktor.application.*
import io.ktor.application.newapi.*
import io.ktor.application.newapi.KtorFeature.Companion.makeFeature
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*
import io.ktor.util.date.*
import kotlinx.atomicfu.*
import java.util.*


/**
 * Configuration for [DefaultHeaders] feature.
 */
public class DefaultHeadersConfig(public val pipeline: ApplicationCallPipeline) {
    /**
     * Provides a builder to append any custom headers to be sent with each request
     */
    internal val headers = HeadersBuilder()

    /**
     * Adds standard header property [name] with the specified [value].
     */
    public fun header(name: String, value: String): Unit = headers.append(name, value)

    /**
     * Provides time source. Useful for testing.
     */
    @InternalAPI
    public var clock: () -> Long = { System.currentTimeMillis() }


    private val headersBuilt = headers.build()

    private var cachedDateTimeStamp: Long = 0L
    private val cachedDateText = atomic("")


    internal fun intercept(call: ApplicationCall) {
        appendDateHeader(call)
        headersBuilt.forEach { name, value -> value.forEach { call.response.header(name, it) } }
    }

    private fun appendDateHeader(call: ApplicationCall) {
        val captureCached = cachedDateTimeStamp
        val currentTimeStamp = clock()
        if (captureCached + DATE_CACHE_TIMEOUT_MILLISECONDS <= currentTimeStamp) {
            cachedDateTimeStamp = currentTimeStamp
            cachedDateText.value = now(currentTimeStamp).toHttpDate()
        }
        call.response.header(HttpHeaders.Date, cachedDateText.value)
    }

    private fun now(time: Long): GMTDate {
        return calendar.get().toDate(time)
    }
}

private const val DATE_CACHE_TIMEOUT_MILLISECONDS = 1000

private val GMT_TIMEZONE = TimeZone.getTimeZone("GMT")!!

private val calendar = object : ThreadLocal<Calendar>() {
    override fun initialValue(): Calendar {
        return Calendar.getInstance(GMT_TIMEZONE, Locale.ROOT)
    }
}

/**
 * Adds standard HTTP headers `Date` and `Server` and provides ability to specify other headers
 * that are included in responses.
 */
public val DefaultHeaders: KtorFeature<DefaultHeadersConfig> = makeFeature("DefaultHeaders", ::DefaultHeadersConfig) {
    if (feature.headers.getAll(HttpHeaders.Server) == null) {
        val applicationClass = feature.pipeline.javaClass

        val ktorPackageName: String = Application::class.java.`package`.implementationTitle ?: "ktor"
        val ktorPackageVersion: String = Application::class.java.`package`.implementationVersion ?: "debug"
        val applicationPackageName: String =
            applicationClass.`package`.implementationTitle ?: applicationClass.simpleName
        val applicationPackageVersion: String = applicationClass.`package`.implementationVersion ?: "debug"

        feature.headers.append(
            HttpHeaders.Server,
            "$applicationPackageName/$applicationPackageVersion $ktorPackageName/$ktorPackageVersion"
        )
    }


    onCall { call ->
        feature.intercept(call)
    }
}
