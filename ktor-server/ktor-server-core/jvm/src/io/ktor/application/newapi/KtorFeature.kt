/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.application.newapi

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*
import io.ktor.util.pipeline.*

public interface FeatureContext {
    public fun onCall(callback: (ApplicationCall) -> Unit): Unit

    public fun onReceive(callback: (ApplicationCall) -> Unit): Unit

    public fun onSend(callback: (ApplicationCall) -> Unit): Unit
}

public abstract class KtorFeature<Configuration : Any>(
    public val name: String
) :
    ApplicationFeature<ApplicationCallPipeline, Configuration, KtorFeature<Configuration>>, FeatureContext {

    override val key: AttributeKey<KtorFeature<Configuration>> = AttributeKey(name)

    public abstract val configuration: Configuration

    public data class Interception(
        val phase: PipelinePhase,
        val action: (Pipeline<*, ApplicationCall>) -> Unit
    )

    protected val callInterceptions: MutableList<Interception> = mutableListOf()
    protected val receiveInterceptions: MutableList<Interception> = mutableListOf()
    protected val sendInterceptions: MutableList<Interception> = mutableListOf()

    private fun onDefaultPhase(
        interceptions: MutableList<Interception>,
        phase: PipelinePhase,
        callback: (ApplicationCall) -> Unit
    ) {
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
            feature.onDefaultPhase(feature.callInterceptions, ApplicationCallPipeline.Monitoring, callback)

        public fun setup(callback: (ApplicationCall) -> Unit): Unit =
            feature.onDefaultPhase(feature.callInterceptions, ApplicationCallPipeline.Setup, callback)

        public fun fallback(callback: (ApplicationCall) -> Unit): Unit =
            feature.onDefaultPhase(feature.callInterceptions, ApplicationCallPipeline.Fallback, callback)

        public fun middleware(callback: (ApplicationCall) -> Unit): Unit =
            feature.onDefaultPhase(feature.callInterceptions, ApplicationCallPipeline.Features, callback)
    }

    // AppilicationCallPipeline interceptor
    public fun onCallBuilder(build: CallContext.() -> Unit): Unit =
        CallContext(this).build()

    // default
    public override fun onCall(callback: (ApplicationCall) -> Unit): Unit =
        onCallBuilder {
            middleware {
                callback(it)
            }
        }


    public class ReceiveContext(private val feature: KtorFeature<*>) {
        public fun beforeReceive(callback: (ApplicationCall) -> Unit): Unit =
            feature.onDefaultPhase(feature.receiveInterceptions, ApplicationReceivePipeline.Before, callback)

        public fun afterReceive(callback: (ApplicationCall) -> Unit): Unit =
            feature.onDefaultPhase(feature.receiveInterceptions, ApplicationReceivePipeline.After, callback)

        public fun onReceive(callback: (ApplicationCall) -> Unit): Unit =
            feature.onDefaultPhase(feature.receiveInterceptions, ApplicationReceivePipeline.Transform, callback)
    }

    // ApplicationReceivePipeline interceptor
    public fun onCallReceiveBuilder(build: ReceiveContext.() -> Unit): Unit =
        ReceiveContext(this).build()

    // default
    public override fun onReceive(callback: (ApplicationCall) -> Unit): Unit =
        onCallReceiveBuilder {
            onReceive {
                callback(it)
            }
        }

    public class SendContext(private val feature: KtorFeature<*>) {
        public fun beforeSend(callback: (ApplicationCall) -> Unit): Unit =
            feature.onDefaultPhase(feature.sendInterceptions, ApplicationSendPipeline.Before, callback)

        public fun afterSend(callback: (ApplicationCall) -> Unit): Unit =
            feature.onDefaultPhase(feature.sendInterceptions, ApplicationSendPipeline.After, callback)

        public fun onSend(callback: (ApplicationCall) -> Unit): Unit =
            feature.onDefaultPhase(feature.sendInterceptions, ApplicationSendPipeline.Transform, callback)

        public fun contentEncoding(callback: (ApplicationCall) -> Unit): Unit =
            feature.onDefaultPhase(feature.sendInterceptions, ApplicationSendPipeline.ContentEncoding, callback)
    }

    // ApplicationSendPipeline interceptor
    public fun onCallSendBuilder(build: SendContext.() -> Unit): Unit =
        SendContext(this).build()

    // default
    public override fun onSend(callback: (ApplicationCall) -> Unit): Unit =
        onCallSendBuilder {
            onSend {
                callback(it)
            }
        }


    private var index = 0
    private fun newPhase(): PipelinePhase = PipelinePhase("${name}Phase${index++}")

    public abstract class RelativeFeatureContext(private val feature: KtorFeature<*>) : FeatureContext {
        protected fun sortedPhases(
            interceptions: List<Interception>,
            pipeline: Pipeline<*, ApplicationCall>
        ): List<PipelinePhase> =
            interceptions
                .map { it.phase }
                .sortedBy {
                    if (!pipeline.items.contains(it)) {
                        throw FeatureNotInstalledException(feature)
                    }

                    pipeline.items.indexOf(it)
                }

        public abstract fun selectPhase(phases: List<PipelinePhase>): PipelinePhase?

        public abstract fun insertPhase(
            pipeline: Pipeline<*, ApplicationCall>,
            relativePhase: PipelinePhase,
            newPhase: PipelinePhase
        )

        private fun onDefaultPhase(interceptions: MutableList<Interception>, callback: (ApplicationCall) -> Unit) {
            val currentPhase = feature.newPhase()

            interceptions.add(
                Interception(
                    currentPhase,
                    action = { pipeline ->
                        val phases = sortedPhases(feature.callInterceptions, pipeline)
                        selectPhase(phases)?.let { lastDependentPhase ->
                            insertPhase(pipeline, lastDependentPhase, currentPhase)
                        }
                        pipeline.intercept(currentPhase) {
                            callback(call)
                        }
                    })
            )
        }

        override fun onCall(callback: (ApplicationCall) -> Unit): Unit =
            onDefaultPhase(feature.callInterceptions, callback)

        override fun onReceive(callback: (ApplicationCall) -> Unit): Unit =
            onDefaultPhase(feature.receiveInterceptions, callback)

        override fun onSend(callback: (ApplicationCall) -> Unit): Unit =
            onDefaultPhase(feature.sendInterceptions, callback)
    }

    public class AfterFeatureContext(feature: KtorFeature<*>) : RelativeFeatureContext(feature) {
        override fun selectPhase(phases: List<PipelinePhase>): PipelinePhase? = phases.lastOrNull()

        override fun insertPhase(
            pipeline: Pipeline<*, ApplicationCall>,
            relativePhase: PipelinePhase,
            newPhase: PipelinePhase
        ) {
            pipeline.insertPhaseAfter(relativePhase, newPhase)
        }

    }

    public class BeforeFeatureContext(feature: KtorFeature<*>) : RelativeFeatureContext(feature) {
        override fun selectPhase(phases: List<PipelinePhase>): PipelinePhase? = phases.firstOrNull()

        override fun insertPhase(
            pipeline: Pipeline<*, ApplicationCall>,
            relativePhase: PipelinePhase,
            newPhase: PipelinePhase
        ) {
            pipeline.insertPhaseBefore(relativePhase, newPhase)
        }

    }

    public fun afterFeature(feature: KtorFeature<*>, build: AfterFeatureContext.() -> Unit): Unit =
        AfterFeatureContext(this).build()


    public fun beforeFeature(feature: KtorFeature<*>, build: BeforeFeatureContext.() -> Unit): Unit =
        BeforeFeatureContext(this).build()

    public companion object {
        public fun <Configuration : Any> makeFeature(
            name: String,
            initialConfiguration: Configuration,
            body: KtorFeature<Configuration>.() -> Unit
        ): KtorFeature<Configuration> = object : KtorFeature<Configuration>(name) {

            override val configuration: Configuration = initialConfiguration

            override fun install(
                callPipeline: ApplicationCallPipeline,
                configure: Configuration.() -> Unit
            ): KtorFeature<Configuration> {
                configuration.configure()

                this.apply(body)

                callInterceptions.forEach {
                    it.action(callPipeline)
                }

                receiveInterceptions.forEach {
                    it.action(callPipeline.receivePipeline)
                }

                sendInterceptions.forEach {
                    it.action(callPipeline.sendPipeline)
                }

                return this
            }
        }
    }
}
