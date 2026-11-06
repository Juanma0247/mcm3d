package cc.juanma.mcm3d.core.session

import cc.juanma.mcm3d.core.baro.AltitudeFusion
import cc.juanma.mcm3d.core.route.TrackPoint
import java.io.BufferedReader
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

class SessionMark(val number: Int, val timeMs: Long)

/** A diagnostic field-test recording turned into a track: positions with fused altitude and wall-clock times. */
class SessionTrack(val startTimeMs: Long, val points: List<TrackPoint>, val marks: List<SessionMark>)

/**
 * Reads the CSV written by the field-test recorder (formats 1 and 2). Only FIX, BARO and MARK rows
 * matter here; altitude is rebuilt with the same barometer + GNSS fusion used for outings.
 */
object SessionCsv {
  private const val MAX_ACCURACY_M = 30.0
  private const val GAP_MS = 60_000L

  fun read(reader: BufferedReader): SessionTrack {
    var startMs = Long.MIN_VALUE
    val fusion = AltitudeFusion()
    val points = ArrayList<TrackPoint>()
    val marks = ArrayList<SessionMark>()
    var lastFixMs = Long.MIN_VALUE
    reader.lineSequence().forEach { line ->
      if (line.startsWith("# start=")) {
        startMs = parseStart(line)
        return@forEach
      }
      if (line.isEmpty() || line[0] == '#') return@forEach
      val f = line.split(',')
      val t = f.getOrNull(1)?.toLongOrNull() ?: return@forEach
      when (f[0]) {
        "BARO" -> f.getOrNull(2)?.toDoubleOrNull()?.let(fusion::onPressure)
        "MARK" -> f.getOrNull(2)?.toIntOrNull()?.let { marks.add(SessionMark(it, t)) }
        "FIX" -> {
          val lat = f.getOrNull(2)?.toDoubleOrNull() ?: return@forEach
          val lon = f.getOrNull(3)?.toDoubleOrNull() ?: return@forEach
          val accuracy = f.getOrNull(4)?.toDoubleOrNull() ?: Double.NaN
          val altitude = f.getOrNull(6)?.toDoubleOrNull() ?: f.getOrNull(5)?.toDoubleOrNull()
          val verticalAccuracy = f.getOrNull(7)?.toDoubleOrNull() ?: Double.NaN
          if (lastFixMs != Long.MIN_VALUE && t - lastFixMs > GAP_MS) fusion.relax(t - lastFixMs)
          lastFixMs = t
          if (altitude != null) fusion.onGnss(t, altitude, verticalAccuracy)
          if (!accuracy.isNaN() && accuracy > MAX_ACCURACY_M) return@forEach
          val time = if (startMs == Long.MIN_VALUE) TrackPoint.NO_TIME else startMs + t
          points.add(TrackPoint(lat, lon, fusion.altitude(), time))
        }
      }
    }
    val absoluteMarks = if (startMs == Long.MIN_VALUE) emptyList() else marks.map { SessionMark(it.number, startMs + it.timeMs) }
    return SessionTrack(startMs, points, absoluteMarks)
  }

  private fun parseStart(line: String): Long = try {
    OffsetDateTime.parse(line.removePrefix("# start=").substringBefore(' ').trim()).toInstant().toEpochMilli()
  } catch (error: DateTimeParseException) {
    Long.MIN_VALUE
  }
}
