package cc.juanma.mcm3d.zone

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import cc.juanma.mcm3d.appGraph
import cc.juanma.mcm3d.core.zone.ZoneCell
import cc.juanma.mcm3d.core.zone.ZoneGrid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Downloads map cells one after another (Overpass allows few parallel requests). Requests are
 * chained in a single queue, and cells already on disk are skipped.
 */
class ZoneWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val store = applicationContext.appGraph.zones
    val cells = inputData.getNullableStringArray(KEY_CELLS).orEmpty().mapNotNull { it?.let(::parseCell) }
    return withContext(Dispatchers.IO) {
      try {
        for (cell in store.missing(cells)) {
          store.download(cell)
          delay(PAUSE_BETWEEN_MS)
        }
        Result.success()
      } catch (error: IOException) {
        if (runAttemptCount + 1 >= MAX_ATTEMPTS) Result.failure() else Result.retry()
      }
    }
  }

  companion object {
    private const val KEY_CELLS = "cells"
    private const val QUEUE = "zones"
    private const val MAX_ATTEMPTS = 6
    private const val PAUSE_BETWEEN_MS = 1_000L
    private const val AROUND_USER_M = 1_500.0
    private const val ALONG_ROUTE_M = 400.0

    fun ensureAround(context: Context, lat: Double, lon: Double) =
      ensure(context, ZoneGrid.cellsAround(lat, lon, AROUND_USER_M))

    fun ensureAlong(context: Context, lats: DoubleArray, lons: DoubleArray) =
      ensure(context, ZoneGrid.cellsAlong(lats, lons, ALONG_ROUTE_M))

    private fun ensure(context: Context, cells: Collection<ZoneCell>) {
      val missing = context.appGraph.zones.missing(cells)
      if (missing.isEmpty()) return
      val request = OneTimeWorkRequestBuilder<ZoneWorker>()
        .setInputData(workDataOf(KEY_CELLS to missing.map { it.key }.toTypedArray()))
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build()
      WorkManager.getInstance(context).enqueueUniqueWork(QUEUE, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private fun parseCell(key: String): ZoneCell? {
      val parts = key.split('_')
      val x = parts.getOrNull(0)?.toIntOrNull() ?: return null
      val y = parts.getOrNull(1)?.toIntOrNull() ?: return null
      return ZoneCell(x, y)
    }
  }
}
