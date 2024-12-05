package com.sksamuel.cohort.endpoints

import com.sksamuel.cohort.CohortConfigAttributeKey
import com.sksamuel.cohort.gc.getGcInfo
import com.sksamuel.cohort.heap.getHeapDump
import com.sksamuel.cohort.jvm.getJvmDetails
import com.sksamuel.cohort.logging.LogInfo
import com.sksamuel.cohort.memory.getMemoryInfo
import com.sksamuel.cohort.os.getOperatingSystem
import com.sksamuel.cohort.system.getSysProps
import com.sksamuel.cohort.threads.getThreadDump
import com.sksamuel.tabby.results.sequence
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.util.getOrFail
import java.time.ZoneOffset

fun Route.cohort(configure: CohortConfiguration.() -> Unit = {}) {

   val config = this.application.attributes.getOrNull(CohortConfigAttributeKey) ?: CohortConfiguration()
   config.configure()

   if (config.heapDump) {
      get("${config.endpointPrefix}/heapdump") {
         getHeapDump().fold(
            { call.respond(HttpStatusCode.OK, it) },
            { call.respondText(it.stackTraceToString(), ContentType.Text.Plain, HttpStatusCode.InternalServerError) },
         )
      }
   }

   if (config.memory) {
      get("${config.endpointPrefix}/memory") {
         getMemoryInfo().fold(
            { call.respondText(it.toJson(), ContentType.Application.Json, HttpStatusCode.OK) },
            { call.respondText(it.stackTraceToString(), ContentType.Text.Plain, HttpStatusCode.InternalServerError) },
         )
      }
   }

   config.dataSources.let { dsm ->
      if (dsm.isNotEmpty()) {
         get("${config.endpointPrefix}/datasources") {
            dsm.map { it.info() }.sequence().fold(
               { call.respondText(it.toJson(), ContentType.Application.Json, HttpStatusCode.OK) },
               {
                  call.respondText(
                     it.stackTraceToString(),
                     ContentType.Text.Plain,
                     HttpStatusCode.InternalServerError
                  )
               }
            )
         }
      }
   }

   config.migrations?.let { m ->
      get("${config.endpointPrefix}/dbmigration") {
         m.migrations().fold(
            { call.respondText(it.toJson(), ContentType.Application.Json, HttpStatusCode.OK) },
            { call.respondText(it.stackTraceToString(), ContentType.Text.Plain, HttpStatusCode.InternalServerError) },
         )
      }
   }

   config.logManager?.let { manager ->

      get("${config.endpointPrefix}/logging") {
         runCatching {
            val levels = manager.levels()
            val loggers = manager.loggers()
            LogInfo(levels, loggers).toJson()
         }.fold(
            { call.respondText(it, ContentType.Application.Json, HttpStatusCode.OK) },
            { call.respondText(it.stackTraceToString(), ContentType.Text.Plain, HttpStatusCode.InternalServerError) },
         )
      }

      put("${config.endpointPrefix}/logging/{name}/{level}") {
         val name = call.parameters.getOrFail("name")
         val level = call.parameters.getOrFail("level")
         manager.set(name, level).fold(
            { call.respond(HttpStatusCode.OK) },
            { call.respondText(it.stackTraceToString(), ContentType.Text.Plain, HttpStatusCode.InternalServerError) },
         )
      }
   }

   if (config.jvmInfo) {
      get("${config.endpointPrefix}/jvm") {
         getJvmDetails().fold(
            { call.respondText(it.toJson(), ContentType.Application.Json, HttpStatusCode.OK) },
            { call.respondText(it.stackTraceToString(), ContentType.Text.Plain, HttpStatusCode.InternalServerError) },
         )
      }
   }

   if (config.gc) {
      get("${config.endpointPrefix}/gc") {
         getGcInfo().fold(
            { call.respondText(it.toJson(), ContentType.Application.Json, HttpStatusCode.OK) },
            { call.respondText(it.stackTraceToString(), ContentType.Text.Plain, HttpStatusCode.InternalServerError) },
         )
      }
   }

   if (config.threadDump) {
      get("${config.endpointPrefix}/threaddump") {
         getThreadDump().fold(
            { call.respondText(it, ContentType.Text.Plain, HttpStatusCode.OK) },
            { call.respondText(it.stackTraceToString(), ContentType.Text.Plain, HttpStatusCode.InternalServerError) },
         )
      }
   }

   if (config.sysprops) {
      get("${config.endpointPrefix}/sysprops") {
         getSysProps().fold(
            { call.respondText(it.toJson(), ContentType.Application.Json, HttpStatusCode.OK) },
            { call.respondText(it.stackTraceToString(), ContentType.Text.Plain, HttpStatusCode.InternalServerError) },
         )
      }
   }

   if (config.operatingSystem) {
      get("${config.endpointPrefix}/os") {
         getOperatingSystem().fold(
            { call.respondText(it.toJson(), ContentType.Application.Json, HttpStatusCode.OK) },
            { call.respondText(it.stackTraceToString(), ContentType.Text.Plain, HttpStatusCode.InternalServerError) },
         )
      }
   }

   config.healthchecks.forEach { (endpoint, registry) ->
      get(endpoint) {

         val status = registry.status()

         val results = status.healthchecks.map {
            ResultJson(
               name = it.key,
               status = it.value.result.status,
               lastCheck = it.value.timestamp.atOffset(ZoneOffset.UTC).toString(),
               message = it.value.result.message,
               cause = it.value.result.cause?.stackTraceToString(),
               consecutiveSuccesses = it.value.consecutiveSuccesses,
               consecutiveFailures = it.value.consecutiveFailures,
            )
         }

         val httpStatusCode = when (status.healthy) {
            true -> HttpStatusCode.OK
            false -> HttpStatusCode.ServiceUnavailable
         }

         call.respondText(results.toJson(), ContentType.Application.Json, httpStatusCode)
      }
   }
}
