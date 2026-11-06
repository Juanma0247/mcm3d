package cc.juanma.mcm3d.elevation

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
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Recalculates an imported route's elevations from the elevation model; waits for a connection if needed. */
class ElevationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val routeId = inputData.getLong(KEY_ROUTE_ID, -1L)
    if (routeId < 0) return Result.failure()
    val routes = applicationContext.appGraph.routes
    return try {
      routes.applyModelElevations(routeId)
      Result.success()
    } catch (error: IOException) {
      if (runAttemptCount + 1 >= MAX_ATTEMPTS) {
        routes.keepFileElevations(routeId)
        Result.failure()
      } else {
        Result.retry()
      }
    }
  }

  companion object {
    private const val KEY_ROUTE_ID = "route_id"
    private const val MAX_ATTEMPTS = 8

    fun schedule(context: Context, routeId: Long) {
      val request = OneTimeWorkRequestBuilder<ElevationWorker>()
        .setInputData(workDataOf(KEY_ROUTE_ID to routeId))
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build()
      WorkManager.getInstance(context).enqueueUniqueWork(name(routeId), ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context, routeId: Long) {
      WorkManager.getInstance(context).cancelUniqueWork(name(routeId))
    }

    private fun name(routeId: Long) = "elevation-$routeId"
  }
}
