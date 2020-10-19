/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.application.newapi

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*
import io.ktor.util.pipeline.*

public abstract class KtorFeature<Configuration : Any>(
    public val name: String
) :
    ApplicationFeature<ApplicationCallPipeline, Configuration, KtorFeature<Configuration>> {

    override val key: AttributeKey<KtorFeature<Configuration>> = AttributeKey(name)

    protected data class Interception(
        val phase: PipelinePhase,
        val action: (Pipeline<Unit, ApplicationCall>) -> Unit
    )

    protected val interceptions: MutableList<Interception> = mutableListOf()

    public abstract val configuration: Configuration

    private fun onDefaultPhase(phase: PipelinePhase, callback: (ApplicationCall) -> Unit) {
        interceptions.add(Interception(
            phase,
            action = { pipeline ->
                pipeline.intercept(phase) {
                    callback(call)
                }
            }
        ))
    }


    public class CallContext(private val feature: KtorFeature<*>) {
        public fun monitoring(callback: (ApplicationCall) -> Unit): Unit =
            feature.onDefaultPhase(ApplicationCallPipeline.Monitoring, callback)

        public fun setup(callback: (ApplicationCall) -> Unit): Unit =
            feature.onDefaultPhase(ApplicationCallPipeline.Setup, callback)

        public fun fallback(callback: (ApplicationCall) -> Unit): Unit =
            feature.onDefaultPhase(ApplicationCallPipeline.Fallback, callback)

        public fun middleware(callback: (ApplicationCall) -> Unit): Unit =
            feature.onDefaultPhase(ApplicationCallPipeline.Features, callback)
    }

    // AppilicationCallPipeline interceptor
    public fun onCallBuilder(build: CallContext.() -> Unit): Unit =
        CallContext(this).build()

    // default
    public fun onCall(callback: (ApplicationCall) -> Unit): Unit =
        onCallBuilder {
            middleware {
                callback(it)
            }
        }


    public class ReceiveContext(private val feature: KtorFeature<*>) {
        public fun beforeReceive(callback: (ApplicationCall) -> Unit): Unit =
            feature.onDefaultPhase(ApplicationReceivePipeline.Before, callback)

        public fun afterReceive(callback: (ApplicationCall) -> Unit): Unit =
            feature.onDefaultPhase(ApplicationReceivePipeline.After, callback)

        public fun onReceive(callback: (ApplicationCall) -> Unit): Unit =
            feature.onDefaultPhase(ApplicationReceivePipeline.Transform, callback)
    }

    // ApplicationReceivePipeline interceptor
    public fun onCallReceiveBuilder(build: ReceiveContext.() -> Unit): Unit =
        ReceiveContext(this).build()

    // default
    public fun onCallReceive(callback: (ApplicationCall) -> Unit): Unit =
        onCallReceiveBuilder {
            onReceive {
                callback(it)
            }
        }

    public class SendContext(private val feature: KtorFeature<*>) {
        public fun beforeSend(callback: (ApplicationCall) -> Unit): Unit =
            feature.onDefaultPhase(ApplicationSendPipeline.Before, callback)

        public fun afterSend(callback: (ApplicationCall) -> Unit): Unit =
            feature.onDefaultPhase(ApplicationSendPipeline.After, callback)

        public fun onSend(callback: (ApplicationCall) -> Unit): Unit =
            feature.onDefaultPhase(ApplicationSendPipeline.Transform, callback)

        public fun contentEncoding(callback: (ApplicationCall) -> Unit): Unit =
            feature.onDefaultPhase(ApplicationSendPipeline.ContentEncoding, callback)
    }

    // ApplicationSendPipeline interceptor
    public fun onCallSendBuilder(build: SendContext.() -> Unit): Unit =
        SendContext(this).build()

    // default
    public fun onCallSend(callback: (ApplicationCall) -> Unit): Unit =
        onCallSendBuilder {
            onSend {
                callback(it)
            }
        }

    protected val phases: List<PipelinePhase> = interceptions.map { it.phase }

    protected fun sortedPhases(pipeline: Pipeline<Unit, ApplicationCall>): List<PipelinePhase> = phases.sortedBy {
        if (!pipeline.items.contains(it)) {
            throw FeatureNotInstalledException(this)
        }

        pipeline.items.indexOf(it)
    }

    private var index = 0

    private fun newPhase(): PipelinePhase = PipelinePhase("${name}Phase${index++}")

    public fun afterFeature(feature: KtorFeature<*>, callback: (ApplicationCall) -> Unit) {
        val currentPhase = newPhase()

        interceptions.add(Interception(
            phase = ApplicationCallPipeline.Setup,
            action = { pipeline ->
                feature.sortedPhases(pipeline).lastOrNull()?.let { lastDependentPhase ->
                    pipeline.insertPhaseAfter(lastDependentPhase, currentPhase)
                }

                pipeline.intercept(currentPhase) {
                    callback(call)
                }
            }
        ))
    }

    public fun beforeFeature(feature: KtorFeature<*>, callback: (ApplicationCall) -> Unit) {
        val currentPhase = newPhase()

        interceptions.add(Interception(
            phase = currentPhase,
            action = { pipeline ->
                feature.sortedPhases(pipeline).firstOrNull()?.let { firstDependentPhase ->
                    pipeline.insertPhaseBefore(firstDependentPhase, currentPhase)
                }

                pipeline.intercept(currentPhase) {
                    callback(call)
                }
            }
        ))
    }

    public companion object {
        public fun <Configuration : Any> makeFeature(
            name: String,
            initialConfiguration: Configuration,
            body: KtorFeature<Configuration>.() -> Unit
        ): KtorFeature<Configuration> = object : KtorFeature<Configuration>(name) {

            override val configuration: Configuration = initialConfiguration

            override fun install(
                pipeline: ApplicationCallPipeline,
                configure: Configuration.() -> Unit
            ): KtorFeature<Configuration> {
                configuration.configure()

                this.apply(body)

                interceptions.forEach {
                    it.action(pipeline)
                }

                return this
            }
        }
    }
}
