/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.features

import io.ktor.application.newapi.*
import io.ktor.application.newapi.KtorFeature.Companion.makeFeature
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.util.*
import io.ktor.util.pipeline.*

private val HeadPhase = PipelinePhase("HEAD")

/**
 * A feature that automatically respond to HEAD requests
 */
public val AutoHeadResponse: KtorFeature<Unit> = makeFeature("AutoHeadResponse", {}) {

    onCall {
        if (call.request.local.method == HttpMethod.Head) {
            call.response.pipeline.insertPhaseBefore(ApplicationSendPipeline.TransferEncoding, HeadPhase)
            call.response.pipeline.intercept(HeadPhase) { message ->
                if (message is OutgoingContent && message !is OutgoingContent.NoContent) {
                    proceedWith(HeadResponse(message))
                }
            }

            // Pretend the request was with GET method so that all normal routes and interceptors work
            // but in the end we will drop the content
            call.mutableOriginConnectionPoint.method = HttpMethod.Get
        }
    }
}

private class HeadResponse(val original: OutgoingContent) : OutgoingContent.NoContent() {
    override val status: HttpStatusCode? get() = original.status
    override val contentType: ContentType? get() = original.contentType
    override val contentLength: Long? get() = original.contentLength
    override fun <T : Any> getProperty(key: AttributeKey<T>) = original.getProperty(key)
    override fun <T : Any> setProperty(key: AttributeKey<T>, value: T?) = original.setProperty(key, value)
    override val headers get() = original.headers
}
