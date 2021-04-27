/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.features

import io.ktor.application.*
import io.ktor.application.newapi.*
import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.util.*
import io.ktor.application.newapi.KtorPlugin.Companion.createPlugin as makePlugin
import io.ktor.util.pipeline.*


/**
 * Configuration for [CachingHeaders] feature
 */
public class CachingHeadersConfig {
    internal val optionsProviders = mutableListOf<(OutgoingContent) -> CachingOptions?>()

    init {
        optionsProviders.add { content -> content.caching }
    }

    /**
     * Registers a function that can provide caching options for a given [OutgoingContent]
     */
    public fun options(provider: (OutgoingContent) -> CachingOptions?) {
        optionsProviders.add(provider)
    }

    /**
     * Retrieves caching options for a given content
     */
    public fun optionsFor(content: OutgoingContent): List<CachingOptions> {
        return optionsProviders.mapNotNullTo(ArrayList(optionsProviders.size)) { it(content) }
    }
}

/**
 * Feature that set [CachingOptions] headers for every response.
 * It invokes [optionsProviders] for every response and use first non null caching options
 */
public val CachingHeaders: KtorPlugin<CachingHeadersConfig> = makePlugin(
    "CachingHeaders",
    { CachingHeadersConfig() }
) {
    onResponse.after {
        val message = subject
        val options = if (message is OutgoingContent) plugin.optionsFor(message) else emptyList()

        if (options.isNotEmpty()) {
            val headers = Headers.build {
                options.mapNotNull { it.cacheControl }
                    .mergeCacheControlDirectives()
                    .ifEmpty { null }?.let { directives ->
                        append(HttpHeaders.CacheControl, directives.joinToString(separator = ", "))
                    }
                options.firstOrNull { it.expires != null }?.expires?.let { expires ->
                    append(HttpHeaders.Expires, expires.toHttpDate())
                }
            }

            val responseHeaders = call.response.headers
            headers.forEach { name, values ->
                values.forEach { responseHeaders.append(name, it) }
            }
        }
    }
}
