package com.sksamuel.cohort.redis

import com.sksamuel.cohort.Check
import com.sksamuel.cohort.CheckResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisClientConfig

/**
 * A [Check] that checks that a connection can be made to a redis instance.
 *
 * @param config provides access details to the cluster.
 */
class RedisCheck(
  private val hostsAndPort: HostAndPort,
  private val config: JedisClientConfig,
) : Check {

  override suspend fun check(): CheckResult {
    return withContext(Dispatchers.IO) {
      runCatching {
        val jedis = Jedis(hostsAndPort, config)
        jedis.connection.use { it.ping() }
        CheckResult.Healthy("Connected to redis cluster")
      }.getOrElse {
        CheckResult.Unhealthy("Could not connect to redis at $hostsAndPort", it)
      }
    }
  }
}
