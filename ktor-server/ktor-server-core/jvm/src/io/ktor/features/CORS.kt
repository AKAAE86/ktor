/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("MemberVisibilityCanBePrivate")

package io.ktor.features

import io.ktor.application.*
import io.ktor.application.newapi.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*

public class CorsConfig {
    public companion object {

        private fun caseInsensitiveSet(vararg elements: String): Set<String> =
            CaseInsensitiveSet(elements.asList())

        /**
         * The default CORS max age value
         */
        public const val CORS_DEFAULT_MAX_AGE: Long = 24L * 3600 // 1 day

        /**
         * Default HTTP methods that are always allowed by CORS
         */
        public val CorsDefaultMethods: Set<HttpMethod> = setOf(HttpMethod.Get, HttpMethod.Post, HttpMethod.Head)

        // https://www.w3.org/TR/cors/#simple-header
        /**
         * Default HTTP headers that are always allowed by CORS
         */
        @Suppress("unused")
        @Deprecated(
            "Use CorsSimpleRequestHeaders or CorsSimpleResponseHeaders instead",
            level = DeprecationLevel.ERROR
        )
        public val CorsDefaultHeaders: Set<String> = caseInsensitiveSet(
            HttpHeaders.CacheControl,
            HttpHeaders.ContentLanguage,
            HttpHeaders.ContentType,
            HttpHeaders.Expires,
            HttpHeaders.LastModified,
            HttpHeaders.Pragma
        )

        /**
         * Default HTTP headers that are always allowed by CORS
         * (simple request headers according to https://www.w3.org/TR/cors/#simple-header )
         * Please note that `Content-Type` header simplicity depends on it's value.
         */
        public val CorsSimpleRequestHeaders: Set<String> = caseInsensitiveSet(
            HttpHeaders.Accept,
            HttpHeaders.AcceptLanguage,
            HttpHeaders.ContentLanguage,
            HttpHeaders.ContentType
        )

        /**
         * Default HTTP headers that are always allowed by CORS to be used in response
         * (simple request headers according to https://www.w3.org/TR/cors/#simple-header )
         */
        public val CorsSimpleResponseHeaders: Set<String> = caseInsensitiveSet(
            HttpHeaders.CacheControl,
            HttpHeaders.ContentLanguage,
            HttpHeaders.ContentType,
            HttpHeaders.Expires,
            HttpHeaders.LastModified,
            HttpHeaders.Pragma
        )

        /**
         * The allowed set of content types that are allowed by CORS without preflight check
         */
        @Suppress("unused")
        public val CorsSimpleContentTypes: Set<ContentType> =
            setOf(
                ContentType.Application.FormUrlEncoded,
                ContentType.MultiPart.FormData,
                ContentType.Text.Plain
            ).unmodifiable()
    }

    /**
     * Allowed CORS hosts
     */
    public val hosts: MutableSet<String> = HashSet()

    /**
     * Allowed CORS headers
     */
    public val headers: MutableSet<String> = CaseInsensitiveSet()

    /**
     * Allowed HTTP methods
     */
    public val methods: MutableSet<HttpMethod> = HashSet()

    /**
     * Exposed HTTP headers that could be accessed by a client
     */
    public val exposedHeaders: MutableSet<String> = CaseInsensitiveSet()

    /**
     * Allow sending credentials
     */
    public var allowCredentials: Boolean = false

    /**
     * Duration in seconds to tell the client to keep the host in a list of known HSTS hosts.
     */
    public var maxAgeInSeconds: Long = CORS_DEFAULT_MAX_AGE
        set(newMaxAge) {
            check(newMaxAge >= 0L) { "maxAgeInSeconds shouldn't be negative: $newMaxAge" }
            field = newMaxAge
        }

    /**
     * Allow requests from the same origin
     */
    public var allowSameOrigin: Boolean = true

    /**
     * Allow sending requests with non-simple content-types. The following content types are considered simple:
     * - `text/plain`
     * - `application/x-www-form-urlencoded`
     * - `multipart/form-data`
     */
    public var allowNonSimpleContentTypes: Boolean = false

    /**
     * Allow requests from any host
     */
    public fun anyHost() {
        hosts.add("*")
    }

    /**
     * Allow requests from the specified domains and schemes
     */
    public fun host(host: String, schemes: List<String> = listOf("http"), subDomains: List<String> = emptyList()) {
        if (host == "*") {
            return anyHost()
        }
        require("://" !in host) { "scheme should be specified as a separate parameter schemes" }

        for (schema in schemes) {
            hosts.add("$schema://$host")

            for (subDomain in subDomains) {
                hosts.add("$schema://$subDomain.$host")
            }
        }
    }

    /**
     * Allow to expose [header]. It adds the [header] to `Access-Control-Expose-Headers` if it is not a
     * simple response header.
     */
    public fun exposeHeader(header: String) {
        if (header !in CorsSimpleResponseHeaders) {
            exposedHeaders.add(header)
        }
    }

    /**
     * Allow to expose `X-Http-Method-Override` header
     */
    @Deprecated(
        "Allow it in request headers instead",
        ReplaceWith("allowXHttpMethodOverride()"),
        level = DeprecationLevel.ERROR
    )
    public fun exposeXHttpMethodOverride() {
        exposedHeaders.add(HttpHeaders.XHttpMethodOverride)
    }

    /**
     * Allow to send `X-Http-Method-Override` header
     */
    @Suppress("unused")
    public fun allowXHttpMethodOverride() {
        header(HttpHeaders.XHttpMethodOverride)
    }

    /**
     * Allow sending [header]
     */
    public fun header(header: String) {
        if (header.equals(HttpHeaders.ContentType, ignoreCase = true)) {
            allowNonSimpleContentTypes = true
            return
        }

        if (header !in CorsSimpleRequestHeaders) {
            headers.add(header)
        }
    }

    /**
     * Please note that CORS operates ONLY with REAL HTTP methods
     * and will never consider overridden methods via `X-Http-Method-Override`.
     * However you can add them here if you are implementing CORS at client side from the scratch
     * that you generally don't need to do.
     */
    public fun method(method: HttpMethod) {
        if (method !in CorsDefaultMethods) {
            methods.add(method)
        }
    }

    private val numberRegex = "[0-9]+".toRegex()

    /**
     * Allow requests from any origin
     */
    public val allowsAnyHost: Boolean = "*" in hosts

    /**
     * All allowed headers to be sent including simple
     */
    public val allHeaders: Set<String> =
        (headers + CorsSimpleRequestHeaders).let { headers ->
            if (allowNonSimpleContentTypes) headers else headers.minus(HttpHeaders.ContentType)
        }

    /**
     * All allowed HTTP methods
     */
    public val allMethods: Set<HttpMethod> = HashSet<HttpMethod>(methods + CorsDefaultMethods)

    /**
     * Set of all allowed headers
     */
    public val allHeadersSet: Set<String> = allHeaders.map { it.toLowerCasePreservingASCIIRules() }.toSet()

    private val headersListHeaderValue =
        headers.filterNot { it in CorsSimpleRequestHeaders }
            .let { if (allowNonSimpleContentTypes) it + HttpHeaders.ContentType else it }
            .sorted()
            .joinToString(", ")

    private val allMethodsListHeaderValue =
        allMethods.filterNot { it in CorsDefaultMethods }
            .map { it.value }
            .sorted()
            .joinToString(", ")

    private val maxAgeHeaderValue = maxAgeInSeconds.let { if (it > 0) it.toString() else null }
    private val exposedHeadersSorted = when {
        exposedHeaders.isNotEmpty() -> exposedHeaders.sorted().joinToString(", ")
        else -> null
    }

    private val hostsNormalized = HashSet<String>(hosts.map { normalizeOrigin(it) })

    /**
     * Feature's call interceptor that does all the job. Usually there is no need to install it as it is done during
     * feature installation
     */
    public suspend fun intercept(execution: CallExecution) {
        val call = execution.call

        if (!allowsAnyHost || allowCredentials) {
            call.corsVary()
        }

        val origin = call.request.headers.getAll(HttpHeaders.Origin)?.singleOrNull()
            ?.takeIf(this::isValidOrigin)
            ?: return

        if (allowSameOrigin && call.isSameOrigin(origin)) return

        if (!corsCheckOrigins(origin)) {
            execution.respondCorsFailed()
            return
        }

        if (call.request.httpMethod == HttpMethod.Options) {
            call.respondPreflight(origin)
            // TODO: it shouldn't be here, because something else can respond to OPTIONS
            // But if noone else responds, we should respond with OK
            execution.finish()
            return
        }

        if (!call.corsCheckCurrentMethod()) {
            execution.respondCorsFailed()
            return
        }

        call.accessControlAllowOrigin(origin)
        call.accessControlAllowCredentials()

        if (exposedHeadersSorted != null) {
            call.response.header(HttpHeaders.AccessControlExposeHeaders, exposedHeadersSorted)
        }
    }

    private suspend fun ApplicationCall.respondPreflight(origin: String) {
        if (!corsCheckRequestMethod() || !corsCheckRequestHeaders()) {
            respond(HttpStatusCode.Forbidden)
            return
        }

        accessControlAllowOrigin(origin)
        accessControlAllowCredentials()
        if (allMethodsListHeaderValue.isNotEmpty()) {
            response.header(HttpHeaders.AccessControlAllowMethods, allMethodsListHeaderValue)
        }
        if (headersListHeaderValue.isNotEmpty()) {
            response.header(HttpHeaders.AccessControlAllowHeaders, headersListHeaderValue)
        }
        accessControlMaxAge()

        respond(HttpStatusCode.OK)
    }

    private fun ApplicationCall.accessControlAllowOrigin(origin: String) {
        if (allowsAnyHost && !allowCredentials) {
            response.header(HttpHeaders.AccessControlAllowOrigin, "*")
        } else {
            response.header(HttpHeaders.AccessControlAllowOrigin, origin)
        }
    }

    private fun ApplicationCall.corsVary() {
        val vary = response.headers[HttpHeaders.Vary]
        if (vary == null) {
            response.header(HttpHeaders.Vary, HttpHeaders.Origin)
        } else {
            response.header(HttpHeaders.Vary, vary + ", " + HttpHeaders.Origin)
        }
    }

    private fun ApplicationCall.accessControlAllowCredentials() {
        if (allowCredentials) {
            response.header(HttpHeaders.AccessControlAllowCredentials, "true")
        }
    }

    private fun ApplicationCall.accessControlMaxAge() {
        if (maxAgeHeaderValue != null) {
            response.header(HttpHeaders.AccessControlMaxAge, maxAgeHeaderValue)
        }
    }

    private fun ApplicationCall.isSameOrigin(origin: String): Boolean {
        val requestOrigin = "${this.request.origin.scheme}://${this.request.origin.host}:${this.request.origin.port}"
        return normalizeOrigin(requestOrigin) == normalizeOrigin(origin)
    }

    private fun corsCheckOrigins(origin: String): Boolean {
        return allowsAnyHost || normalizeOrigin(origin) in hostsNormalized
    }

    private fun ApplicationCall.corsCheckRequestHeaders(): Boolean {
        val requestHeaders =
            request.headers.getAll(HttpHeaders.AccessControlRequestHeaders)?.flatMap { it.split(",") }?.map {
                it.trim().toLowerCasePreservingASCIIRules()
            } ?: emptyList()

        return requestHeaders.none { it !in allHeadersSet }
    }

    private fun ApplicationCall.corsCheckCurrentMethod(): Boolean {
        return request.httpMethod in allMethods
    }

    private fun ApplicationCall.corsCheckRequestMethod(): Boolean {
        val requestMethod = request.header(HttpHeaders.AccessControlRequestMethod)?.let { HttpMethod(it) }
        return requestMethod != null && requestMethod in allMethods
    }

    private suspend fun CallExecution.respondCorsFailed() {
        call.respond(HttpStatusCode.Forbidden)
        finish()
    }

    private fun isValidOrigin(origin: String): Boolean {
        if (origin.isEmpty()) {
            return false
        }
        if (origin == "null") {
            return true
        }
        if ("%" in origin) {
            return false
        }

        val protoDelimiter = origin.indexOf("://")
        if (protoDelimiter <= 0) {
            return false
        }

        // check proto
        for (index in 0 until protoDelimiter) {
            if (!origin[index].isLetter()) {
                return false
            }
        }

        var portIndex = origin.length
        for (index in protoDelimiter + 3 until origin.length) {
            val ch = origin[index]
            if (ch == ':' || ch == '/') {
                portIndex = index + 1
                break
            }
            if (ch == '?') return false
        }

        for (index in portIndex until origin.length) {
            if (!origin[index].isDigit()) {
                return false
            }
        }

        return true
    }

    private fun normalizeOrigin(origin: String) =
        if (origin == "null" || origin == "*") origin else StringBuilder(origin.length).apply {
            append(origin)

            if (!origin.substringAfterLast(":", "").matches(numberRegex)) {
                val port = when (origin.substringBefore(':')) {
                    "http" -> "80"
                    "https" -> "443"
                    else -> null
                }

                if (port != null) {
                    append(':')
                    append(port)
                }
            }
        }.toString()
}

/**
 * CORS feature. Please read http://ktor.io/servers/features/cors.html first before using it.
 */
public val CORS: KtorFeature<CorsConfig> = KtorFeature.makeFeature("CORS", { CorsConfig() }) {
    onCall {
        feature.intercept(this)
    }
}
