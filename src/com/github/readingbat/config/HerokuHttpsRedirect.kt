/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package com.github.readingbat.config

import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.ApplicationFeature
import io.ktor.application.call
import io.ktor.features.origin
import io.ktor.http.URLProtocol
import io.ktor.util.AttributeKey
import io.ktor.util.KtorExperimentalAPI
import io.ktor.util.url
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Redirect non-secure requests to HTTPS
 */
class HerokuHttpsRedirect(config: Configuration) {
  /**
   * HTTPS host to redirect to
   */
  val host: String = config.host

  /**
   * HTTPS port to redirect to
   */
  val redirectPort: Int = config.sslPort

  /**
   * If it does permanent redirect
   */
  val permanent: Boolean = config.permanentRedirect

  /**
   * The list of call predicates for redirect exclusion.
   * Any call matching any of the predicates will not be redirected by this feature.
   */
  @KtorExperimentalAPI
  val excludePredicates: List<(ApplicationCall) -> Boolean> = config.excludePredicates.toList()

  /**
   * Redirect feature configuration
   */
  class Configuration {
    /**
     * HTTPS host to redirect to
     */
    var host: String = "localhost"

    /**
     * HTTPS port (443 by default) to redirect to
     */
    var sslPort: Int = URLProtocol.HTTPS.defaultPort

    /**
     * Use permanent redirect or temporary
     */
    var permanentRedirect: Boolean = true

    /**
     * The list of call predicates for redirect exclusion.
     * Any call matching any of the predicates will not be redirected by this feature.
     */
    @KtorExperimentalAPI
    val excludePredicates: MutableList<(ApplicationCall) -> Boolean> = ArrayList()

    /**
     * Exclude calls with paths matching the [pathPrefix] from being redirected to https by this feature.
     */
    @KtorExperimentalAPI
    fun excludePrefix(pathPrefix: String) {
      exclude { call ->
        call.request.origin.uri.startsWith(pathPrefix)
      }
    }

    /**
     * Exclude calls with paths matching the [pathSuffix] from being redirected to https by this feature.
     */
    @KtorExperimentalAPI
    fun excludeSuffix(pathSuffix: String) {
      exclude { call ->
        call.request.origin.uri.endsWith(pathSuffix)
      }
    }

    /**
     * Exclude calls matching the [predicate] from being redirected to https by this feature.
     */
    @KtorExperimentalAPI
    fun exclude(predicate: (call: ApplicationCall) -> Boolean) {
      excludePredicates.add(predicate)
    }
  }

  /**
   * Feature installation object
   */
  companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, HerokuHttpsRedirect> {
    override val key = AttributeKey<HerokuHttpsRedirect>("HerokuHttpsRedirect")
    override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): HerokuHttpsRedirect {
      val feature = HerokuHttpsRedirect(Configuration().apply(configure))
      pipeline.intercept(ApplicationCallPipeline.Features) {
        if (call.request.origin.scheme == "http" &&
          feature.excludePredicates.none { predicate -> predicate(call) }
        ) {
          val redirectUrl = call.url { protocol = URLProtocol.HTTPS; host = feature.host; port = feature.redirectPort }
          logger.info { "Redirecting to: $redirectUrl" }
          call.respondRedirect(redirectUrl, feature.permanent)
          finish()
        }
        else {
          logger.info { "Not redirecting: ${call.request.origin.scheme} ${call.request.origin.uri}" }
        }
      }
      return feature
    }
  }
}
