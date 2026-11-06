package cc.juanma.mcm3d.data

import cc.juanma.mcm3d.core.route.TrackCodec
import cc.juanma.mcm3d.core.route.TrackPoint
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** One binary track file per route (format in [TrackCodec]). */
class TrackStore(private val dir: File) {
  private fun file(routeId: Long) = File(dir, "$routeId.trk")

  fun write(routeId: Long, startTimeMs: Long, points: List<TrackPoint>) {
    dir.mkdirs()
    val target = file(routeId)
    val temp = File(dir, "$routeId.trk.tmp")
    temp.writeBytes(TrackCodec.encode(startTimeMs, points))
    if (!temp.renameTo(target)) {
      target.delete()
      if (!temp.renameTo(target)) throw IOException("Cannot replace ${target.name}")
    }
  }

  fun read(routeId: Long): TrackCodec.Track? {
    val target = file(routeId)
    if (!target.exists()) return null
    return try {
      TrackCodec.decode(target.readBytes())
    } catch (error: IllegalArgumentException) {
      null
    }
  }

  fun delete(routeId: Long) {
    file(routeId).delete()
  }

  fun openAppender(routeId: Long, startTimeMs: Long): TrackAppender {
    dir.mkdirs()
    return TrackAppender(file(routeId), startTimeMs)
  }
}

/** Buffers points in memory and appends them in batches, so recording does not write on every fix. */
class TrackAppender(file: File, private val startTimeMs: Long) : Closeable {
  private val stream = FileOutputStream(file, true)
  private val buffer = ByteBuffer.allocate(TrackCodec.RECORD_BYTES * CAPACITY).order(ByteOrder.LITTLE_ENDIAN)
  private var closed = false

  init {
    if (file.length() == 0L) stream.write(TrackCodec.header(startTimeMs))
  }

  fun add(point: TrackPoint) {
    if (closed) return
    if (buffer.remaining() < TrackCodec.RECORD_BYTES) flush()
    TrackCodec.putRecord(buffer, point, startTimeMs)
  }

  fun flush() {
    if (closed || buffer.position() == 0) return
    stream.write(buffer.array(), 0, buffer.position())
    buffer.clear()
  }

  override fun close() {
    if (closed) return
    flush()
    closed = true
    stream.close()
  }

  private companion object {
    const val CAPACITY = 256
  }
}
