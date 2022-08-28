package com.sksamuel.cohort.redis

import com.sksamuel.cohort.HealthCheck
import com.sksamuel.cohort.HealthCheckResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import redis.clients.jedis.Connection
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.Jedis

/**
 * A [HealthCheck] that checks that a connection can be made to a redis instance.
 *
 * @param command an optional command to execute against the redis instance. Defaults to ping.
 */
class RedisConnectionHealthCheck(
  private val hostAndPort: HostPort,
  private val username: String?,
  private val password: String?,
  private val tls: Boolean,
  private val command: (Connection) -> HealthCheckResult = {
    if (it.ping()) {
      HealthCheckResult.Healthy("Connected to redis cluster")
    } else {
      HealthCheckResult.Healthy("Ping to redis cluster failed")
    }
  },
) : HealthCheck {

  override val name: String = "redis"

  override suspend fun check(): HealthCheckResult {
    return withContext(Dispatchers.IO) {
      runCatching {
        val config = DefaultJedisClientConfig.builder().password(password).user(username).ssl(tls).build()
        val jedis = Jedis(HostAndPort(hostAndPort.host, hostAndPort.port), config)
        jedis.connection.use { command(it) }
      }.getOrElse {
        HealthCheckResult.Unhealthy("Could not connect to redis at $hostAndPort", it)
      }
    }
  }
}
