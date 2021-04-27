/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.application.newapi

import io.ktor.application.*
import io.ktor.config.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kotlin.random.*
import kotlinx.coroutines.*

public typealias OnCallHandler = suspend CallExecution.(ApplicationCall) -> Unit

public interface OnCall {
    public operator fun invoke(callback: suspend CallExecution.(ApplicationCall) -> Unit): Unit
    public fun monitoring(callback: suspend CallExecution.(ApplicationCall) -> Unit): Unit
    public fun fallback(callback: suspend CallExecution.(ApplicationCall) -> Unit): Unit
}

public interface OnReceive {
    public operator fun invoke(callback: suspend ReceiveExecution.(ApplicationCall) -> Unit): Unit
    public fun before(callback: suspend ReceiveExecution.(ApplicationCall) -> Unit): Unit
}

public interface OnResponse {
    public operator fun invoke(callback: suspend ResponseExecution.(ApplicationCall) -> Unit): Unit
    public fun before(callback: suspend ResponseExecution.(ApplicationCall) -> Unit): Unit
    public fun after(callback: suspend ResponseExecution.(ApplicationCall) -> Unit): Unit
}

public interface PluginContext {
    public val onCall: OnCall
    public val onReceive: OnReceive
    public val onResponse: OnResponse
}

/**
 * Compatibility class. It describes how (with what new functionality) some particular phase should be intercepted.
 * It is a wrapper over pipeline.intercept(phase) { ... } and is needed to hide old Plugins API functionality
 * */
public data class Interception<T : Any>(
    val phase: PipelinePhase,
    val action: (Pipeline<T, ApplicationCall>) -> Unit
)

/**
 * Compatibility class. Interception class for Call phase
 * */
public typealias CallInterception = Interception<Unit>
/**
 * Compatibility class. Interception class for Receive phase
 * */
public typealias ReceiveInterception = Interception<ApplicationReceiveRequest>
/**
 * Compatibility class. Interception class for Send phase
 * */
public typealias ResponseInterception = Interception<Any>

/**
 * Compatibility class. Every plugin that needs to be compatible with `beforePlugin(...)` and `afterPlugin(...)`
 * needs to implement this class. It defines a list of interceptions (see [Interception]) for phases in different pipelines
 * that are being intercepted by the current plugin.
 *
 * It is needed in order to get first/last phase in each pipeline for the current plugin and create a new phase
 * that is strictly earlier/later in this pipeline than any interception of the current plugin.
 *
 * Note: any [KtorPlugin] instance automatically fills these fields and there is no need to implement them by hand.
 * But if you want to use old plugin API based on pipelines and phases and to make it available for `beforePlugin`/
 * `afterPlugin` methods of the new plugins API, please consider filling the phases your plugin defines by implementing
 * this interface [InterceptionsHolder].
 *
 * Please, use [defineInterceptions] method inside the first init block to simply define all the interceptions for your
 * feature.
 * */
public interface InterceptionsHolder {
    public val name: String get() = this.javaClass.simpleName

    public val fallbackInterceptions: MutableList<CallInterception>
    public val callInterceptions: MutableList<CallInterception>
    public val monitoringInterceptions: MutableList<CallInterception>

    public val beforeReceiveInterceptions: MutableList<ReceiveInterception>
    public val onReceiveInterceptions: MutableList<ReceiveInterception>

    public val beforeResponseInterceptions: MutableList<ResponseInterception>
    public val onResponseInterceptions: MutableList<ResponseInterception>
    public val afterResponseInterceptions: MutableList<ResponseInterception>

    public fun newPhase(): PipelinePhase = PipelinePhase("${name}Phase${Random.nextInt()}")

    public fun defineInterceptions(build: InterceptionsBuilder.() -> Unit) {
        InterceptionsBuilder(this).build()
    }

    /**
     * Builder class that helps to define interceptions for a feature written in old API.
     * */
    public class InterceptionsBuilder(private val holder: InterceptionsHolder) {
        public fun fallback(vararg phases: PipelinePhase = arrayOf(ApplicationCallPipeline.Fallback)): Unit =
            addPhases(holder.fallbackInterceptions, *phases)

        public fun call(vararg phases: PipelinePhase = arrayOf(ApplicationCallPipeline.Features)): Unit =
            addPhases(holder.callInterceptions, *phases)

        public fun monitoring(vararg phases: PipelinePhase = arrayOf(ApplicationCallPipeline.Monitoring)): Unit =
            addPhases(holder.monitoringInterceptions, *phases)

        public fun beforeReceive(vararg phases: PipelinePhase = arrayOf(ApplicationReceivePipeline.Before)): Unit =
            addPhases(holder.beforeReceiveInterceptions, *phases)

        public fun onReceive(vararg phases: PipelinePhase = arrayOf(ApplicationReceivePipeline.Transform)): Unit =
            addPhases(holder.onReceiveInterceptions, *phases)

        public fun beforeResponse(vararg phases: PipelinePhase = arrayOf(ApplicationSendPipeline.Before)): Unit =
            addPhases(holder.beforeResponseInterceptions, *phases)

        public fun onResponse(vararg phases: PipelinePhase = arrayOf(ApplicationSendPipeline.Transform)): Unit =
            addPhases(holder.onResponseInterceptions, *phases)

        public fun afterResponse(vararg phases: PipelinePhase = arrayOf(ApplicationSendPipeline.After)): Unit =
            addPhases(holder.afterResponseInterceptions, *phases)

        private fun <T : Any> addPhases(target: MutableList<Interception<T>>, vararg phases: PipelinePhase) {
            phases.forEach { phase ->
                target.add(Interception(phase) {})
            }
        }
    }
}

/**
 * Empty implementation of [InterceptionsHolder] interface that can be used for simplicity.
 * */
public class DefaultInterceptionsHolder(override val name: String) : InterceptionsHolder {
    override val fallbackInterceptions: MutableList<CallInterception> = mutableListOf()
    override val callInterceptions: MutableList<CallInterception> = mutableListOf()
    override val monitoringInterceptions: MutableList<CallInterception> = mutableListOf()
    override val beforeReceiveInterceptions: MutableList<ReceiveInterception> = mutableListOf()
    override val onReceiveInterceptions: MutableList<ReceiveInterception> = mutableListOf()
    override val beforeResponseInterceptions: MutableList<ResponseInterception> = mutableListOf()
    override val onResponseInterceptions: MutableList<ResponseInterception> = mutableListOf()
    override val afterResponseInterceptions: MutableList<ResponseInterception> = mutableListOf()
}

public inline class Execution<SubjectT : Any>(private val context: PipelineContext<SubjectT, ApplicationCall>) {
    public suspend fun proceed(): SubjectT = context.proceed()
    public suspend fun proceedWith(subectT: SubjectT): SubjectT = context.proceedWith(subectT)
    public fun finish(): Unit = context.finish()
    public val subject: SubjectT get() = context.subject
    public val call: ApplicationCall get() = context.call

    // Useful methods
    public val environment: ApplicationEnvironment get() = context.application.environment
    public val configuration: ApplicationConfig get() = environment.config
    public val port: Int get() = configuration.propertyOrNull("ktor.deployment.port")?.getString()?.toInt() ?: 8080
    public val host: String get() = configuration.propertyOrNull("ktor.deployment.host")?.getString() ?: "0.0.0.0"
    public fun onShutdown(callback: suspend () -> Unit) {
        GlobalScope.launch(context.coroutineContext) {
            callback()
        }
    }
}

public typealias CallExecution = Execution<Unit>
public typealias ReceiveExecution = Execution<ApplicationReceiveRequest>
public typealias ResponseExecution = Execution<Any>

public abstract class KtorPlugin<Configuration : Any>(
    public override val name: String
) : ApplicationFeature<ApplicationCallPipeline, Configuration, KtorPlugin<Configuration>>, PluginContext, InterceptionsHolder {

    protected var configurationValue: Configuration? = null

    public val plugin: Configuration = configurationValue!!

    override val key: AttributeKey<KtorPlugin<Configuration>> = AttributeKey(name)

    override val fallbackInterceptions: MutableList<CallInterception> = mutableListOf()
    override val callInterceptions: MutableList<CallInterception> = mutableListOf()
    override val monitoringInterceptions: MutableList<CallInterception> = mutableListOf()

    override val beforeReceiveInterceptions: MutableList<ReceiveInterception> = mutableListOf()
    override val onReceiveInterceptions: MutableList<ReceiveInterception> = mutableListOf()

    override val beforeResponseInterceptions: MutableList<ResponseInterception> = mutableListOf()
    override val onResponseInterceptions: MutableList<ResponseInterception> = mutableListOf()
    override val afterResponseInterceptions: MutableList<ResponseInterception> = mutableListOf()

    private fun <T : Any> onDefaultPhase(
        interceptions: MutableList<Interception<T>>,
        phase: PipelinePhase,
        callback: suspend Execution<T>.(ApplicationCall) -> Unit
    ) {
        interceptions.add(Interception(
            phase,
            action = { pipeline ->
                pipeline.intercept(phase) { Execution(this).callback(call) }
            }
        ))
    }

    public override val onCall: OnCall = object : OnCall {
        private val plugin = this@KtorPlugin

        override operator fun invoke(callback: suspend CallExecution.(ApplicationCall) -> Unit) {
            plugin.onDefaultPhase(plugin.callInterceptions, ApplicationCallPipeline.Features, callback)
        }

        override fun monitoring(callback: suspend CallExecution.(ApplicationCall) -> Unit) {
            plugin.onDefaultPhase(plugin.monitoringInterceptions, ApplicationCallPipeline.Monitoring, callback)
        }

        override fun fallback(callback: suspend CallExecution.(ApplicationCall) -> Unit) {
            plugin.onDefaultPhase(plugin.fallbackInterceptions, ApplicationCallPipeline.Fallback, callback)
        }
    }


    public override val onReceive: OnReceive = object : OnReceive {
        private val plugin = this@KtorPlugin

        override fun invoke(callback: suspend ReceiveExecution.(ApplicationCall) -> Unit) {
            plugin.onDefaultPhase(plugin.onReceiveInterceptions, ApplicationReceivePipeline.Transform, callback)
        }

        override fun before(callback: suspend ReceiveExecution.(ApplicationCall) -> Unit) {
            plugin.onDefaultPhase(plugin.beforeReceiveInterceptions, ApplicationReceivePipeline.Before, callback)
        }
    }


    public override val onResponse: OnResponse = object : OnResponse {
        private val plugin = this@KtorPlugin

        override fun invoke(callback: suspend ResponseExecution.(ApplicationCall) -> Unit) {
            plugin.onDefaultPhase(plugin.onResponseInterceptions, ApplicationSendPipeline.Transform, callback)
        }

        override fun before(callback: suspend ResponseExecution.(ApplicationCall) -> Unit) {
            plugin.onDefaultPhase(plugin.beforeResponseInterceptions, ApplicationSendPipeline.Before, callback)
        }

        override fun after(callback: suspend ResponseExecution.(ApplicationCall) -> Unit) {
            plugin.onDefaultPhase(plugin.afterResponseInterceptions, ApplicationSendPipeline.After, callback)
        }
    }

    public abstract class RelativePluginContext(private val otherPlugin: InterceptionsHolder) : PluginContext {
        protected fun <T : Any> sortedPhases(
            interceptions: List<Interception<T>>,
            pipeline: Pipeline<*, ApplicationCall>
        ): List<PipelinePhase> =
            interceptions
                .map { it.phase }
                .sortedBy {
                    if (!pipeline.items.contains(it)) {
                        throw PluginNotInstalledException(otherPlugin.name)
                    }

                    pipeline.items.indexOf(it)
                }

        public abstract fun selectPhase(phases: List<PipelinePhase>): PipelinePhase?

        public abstract fun insertPhase(
            pipeline: Pipeline<*, ApplicationCall>,
            relativePhase: PipelinePhase,
            newPhase: PipelinePhase
        )

        private fun <T : Any> insertToPhaseRelatively(
            interceptions: MutableList<Interception<T>>,
            callback: suspend Execution<T>.(ApplicationCall) -> Unit
        ) {
            val currentPhase = otherPlugin.newPhase()

            interceptions.add(
                Interception(
                    currentPhase,
                    action = { pipeline ->
                        val phases = sortedPhases(interceptions, pipeline)
                        selectPhase(phases)?.let { lastDependentPhase ->
                            insertPhase(pipeline, lastDependentPhase, currentPhase)
                        }
                        pipeline.intercept(currentPhase) {
                            Execution(this).callback(call)
                        }
                    }
                )
            )
        }

        override val onCall: OnCall = object : OnCall {
            override operator fun invoke(callback: suspend CallExecution.(ApplicationCall) -> Unit) {
                insertToPhaseRelatively(otherPlugin.callInterceptions, callback)
            }

            override fun monitoring(callback: suspend CallExecution.(ApplicationCall) -> Unit) {
                insertToPhaseRelatively(otherPlugin.monitoringInterceptions, callback)
            }

            override fun fallback(callback: suspend CallExecution.(ApplicationCall) -> Unit) {
                insertToPhaseRelatively(otherPlugin.fallbackInterceptions, callback)
            }
        }

        override val onReceive: OnReceive = object : OnReceive {
            override operator fun invoke(callback: suspend ReceiveExecution.(ApplicationCall) -> Unit) {
                insertToPhaseRelatively(otherPlugin.onReceiveInterceptions, callback)
            }

            override fun before(callback: suspend ReceiveExecution.(ApplicationCall) -> Unit) {
                insertToPhaseRelatively(otherPlugin.beforeReceiveInterceptions, callback)
            }
        }

        override val onResponse: OnResponse = object : OnResponse {
            override operator fun invoke(callback: suspend ResponseExecution.(ApplicationCall) -> Unit) {
                insertToPhaseRelatively(otherPlugin.onResponseInterceptions, callback)
            }

            override fun before(callback: suspend ResponseExecution.(ApplicationCall) -> Unit) {
                insertToPhaseRelatively(otherPlugin.beforeResponseInterceptions, callback)
            }

            override fun after(callback: suspend ResponseExecution.(ApplicationCall) -> Unit) {
                insertToPhaseRelatively(otherPlugin.afterResponseInterceptions, callback)
            }
        }
    }

    public class AfterPluginContext(plugin: InterceptionsHolder) : RelativePluginContext(plugin) {
        override fun selectPhase(phases: List<PipelinePhase>): PipelinePhase? = phases.lastOrNull()

        override fun insertPhase(
            pipeline: Pipeline<*, ApplicationCall>,
            relativePhase: PipelinePhase,
            newPhase: PipelinePhase
        ) {
            pipeline.insertPhaseAfter(relativePhase, newPhase)
        }

    }

    public class BeforePluginContext(plugin: InterceptionsHolder) : RelativePluginContext(plugin) {
        override fun selectPhase(phases: List<PipelinePhase>): PipelinePhase? = phases.firstOrNull()

        override fun insertPhase(
            pipeline: Pipeline<*, ApplicationCall>,
            relativePhase: PipelinePhase,
            newPhase: PipelinePhase
        ) {
            pipeline.insertPhaseBefore(relativePhase, newPhase)
        }

    }

    public fun afterPlugin(plugin: InterceptionsHolder, build: AfterPluginContext.() -> Unit): Unit =
        AfterPluginContext(plugin).build()


    public fun beforePlugin(plugin: InterceptionsHolder, build: BeforePluginContext.() -> Unit): Unit =
        BeforePluginContext(plugin).build()

    public companion object {
        public fun <Configuration : Any> createPlugin(
            name: String,
            createConfiguration: (ApplicationCallPipeline) -> Configuration,
            body: KtorPlugin<Configuration>.() -> Unit
        ): KtorPlugin<Configuration> = object : KtorPlugin<Configuration>(name) {

            override fun install(
                pipeline: ApplicationCallPipeline,
                configure: Configuration.() -> Unit
            ): KtorPlugin<Configuration> {
                configurationValue = createConfiguration(pipeline)
                configurationValue!!.configure()

                this.apply(body)

                fallbackInterceptions.forEach {
                    it.action(pipeline)
                }

                callInterceptions.forEach {
                    it.action(pipeline)
                }

                monitoringInterceptions.forEach {
                    it.action(pipeline)
                }

                beforeReceiveInterceptions.forEach {
                    it.action(pipeline.receivePipeline)
                }

                onReceiveInterceptions.forEach {
                    it.action(pipeline.receivePipeline)
                }

                beforeResponseInterceptions.forEach {
                    it.action(pipeline.sendPipeline)
                }

                onResponseInterceptions.forEach {
                    it.action(pipeline.sendPipeline)
                }

                afterResponseInterceptions.forEach {
                    it.action(pipeline.sendPipeline)
                }

                return this
            }
        }
    }
}
