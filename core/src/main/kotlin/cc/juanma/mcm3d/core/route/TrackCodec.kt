package cc.juanma.mcm3d.core.route

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Track file: a 16-byte header (magic, version, start time) followed by fixed 16-byte records
 * (latitude and longitude in 1e-7 degrees, elevation as float, milliseconds since the start).
 * Fixed records allow appending while recording and reading by index later; the point count is
 * derived from the file size, so a file cut short by a crash is still valid up to its last record.
 */
object TrackCodec {
  const val HEADER_BYTES = 16
  const val RECORD_BYTES = 16
  private const val MAGIC = 0x4D434D54
  private const val VERSION: Byte = 1
  private const val NO_OFFSET = -1
  private const val E7 = 1e7

  class Track(val startTimeMs: Long, val points: List<TrackPoint>)

  fun header(startTimeMs: Long): ByteArray =
    ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
      .putInt(MAGIC)
      .put(VERSION)
      .put(0).put(0).put(0)
      .putLong(startTimeMs)
      .array()

  fun putRecord(buffer: ByteBuffer, point: TrackPoint, startTimeMs: Long) {
    buffer.putInt((point.lat * E7).roundToInt())
    buffer.putInt((point.lon * E7).roundToInt())
    buffer.putFloat(point.elevation.toFloat())
    val offset = if (point.hasTime && startTimeMs != TrackPoint.NO_TIME) {
      (point.timeMs - startTimeMs).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    } else {
      NO_OFFSET
    }
    buffer.putInt(offset)
  }

  fun encode(startTimeMs: Long, points: List<TrackPoint>): ByteArray {
    val buffer = ByteBuffer.allocate(HEADER_BYTES + RECORD_BYTES * points.size).order(ByteOrder.LITTLE_ENDIAN)
    buffer.put(header(startTimeMs))
    for (p in points) putRecord(buffer, p, startTimeMs)
    return buffer.array()
  }

  fun decode(bytes: ByteArray): Track {
    require(bytes.size >= HEADER_BYTES) { "Track file too short" }
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    require(buffer.getInt() == MAGIC) { "Not a track file" }
    val version = buffer.get()
    require(version == VERSION) { "Unsupported track version $version" }
    buffer.position(8)
    val start = buffer.getLong()
    val count = (bytes.size - HEADER_BYTES) / RECORD_BYTES
    val points = ArrayList<TrackPoint>(count)
    repeat(count) {
      val lat = buffer.getInt() / E7
      val lon = buffer.getInt() / E7
      val elevation = buffer.getFloat().toDouble()
      val offset = buffer.getInt()
      val time = if (offset == NO_OFFSET || start == TrackPoint.NO_TIME) TrackPoint.NO_TIME else start + offset
      points.add(TrackPoint(lat, lon, elevation, time))
    }
    return Track(start, points)
  }
}
