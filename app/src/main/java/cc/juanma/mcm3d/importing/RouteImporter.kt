package cc.juanma.mcm3d.importing

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import cc.juanma.mcm3d.core.route.RouteFileReader
import cc.juanma.mcm3d.core.route.RouteParseException
import cc.juanma.mcm3d.data.RouteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

class RouteImporter(private val context: Context, private val repository: RouteRepository) {
  sealed interface Result {
    data class Success(val routeId: Long) : Result
    data class Failure(val reason: FailureReason) : Result
  }

  enum class FailureReason { UNREADABLE, NOT_A_ROUTE, NO_TRACK, MALFORMED, TOO_LARGE }

  suspend fun import(uri: Uri, defaultName: String): Result = withContext(Dispatchers.IO) {
    val bytes = try {
      read(uri) ?: return@withContext Result.Failure(FailureReason.UNREADABLE)
    } catch (error: IOException) {
      return@withContext Result.Failure(FailureReason.UNREADABLE)
    } catch (error: SecurityException) {
      return@withContext Result.Failure(FailureReason.UNREADABLE)
    } catch (error: TooLarge) {
      return@withContext Result.Failure(FailureReason.TOO_LARGE)
    }
    val parsed = try {
      RouteFileReader.read(bytes) { Xml.newPullParser() }
    } catch (error: RouteParseException) {
      return@withContext Result.Failure(
        when (error.reason) {
          RouteParseException.Reason.NOT_A_ROUTE -> FailureReason.NOT_A_ROUTE
          RouteParseException.Reason.NO_TRACK -> FailureReason.NO_TRACK
          RouteParseException.Reason.MALFORMED -> FailureReason.MALFORMED
          RouteParseException.Reason.TOO_LARGE -> FailureReason.TOO_LARGE
        },
      )
    }
    val fileName = displayName(uri)?.substringBeforeLast('.')?.replace('_', ' ')?.trim()
    val id = try {
      repository.importRoute(parsed, fileName?.takeIf { it.isNotEmpty() } ?: defaultName)
    } catch (error: IOException) {
      return@withContext Result.Failure(FailureReason.UNREADABLE)
    }
    Result.Success(id)
  }

  private class TooLarge : Exception()

  private fun read(uri: Uri): ByteArray? {
    val input = context.contentResolver.openInputStream(uri) ?: return null
    return input.use {
      val out = ByteArrayOutputStream()
      val buffer = ByteArray(64 * 1024)
      while (true) {
        val n = it.read(buffer)
        if (n < 0) break
        out.write(buffer, 0, n)
        if (out.size() > RouteFileReader.MAX_BYTES) throw TooLarge()
      }
      out.toByteArray()
    }
  }

  private fun displayName(uri: Uri): String? = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
      if (cursor.moveToFirst()) cursor.getString(0) else null
    }
  } catch (error: RuntimeException) {
    uri.lastPathSegment
  }
}
