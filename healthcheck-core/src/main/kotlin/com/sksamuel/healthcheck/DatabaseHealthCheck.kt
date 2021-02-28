package com.sksamuel.healthcheck

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.sql.DataSource

/**
 * A [HealthCheck] that checks that a connection can be established with a database.
 */
class DatabaseHealthCheck(
  private val ds: DataSource,
  private val query: String = "SELECT 1",
) : HealthCheck {
  override suspend fun check(): HealthCheckResult = withContext(Dispatchers.IO) {
    val conn = ds.connection
    conn.createStatement().executeQuery(query)
    conn.close()
    HealthCheckResult.Healthy("Connected to database successfully")
  }
}
