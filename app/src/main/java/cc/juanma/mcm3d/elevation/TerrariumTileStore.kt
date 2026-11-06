package cc.juanma.mcm3d.elevation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import cc.juanma.mcm3d.BuildConfig
import cc.juanma.mcm3d.core.elevation.Terrarium
import cc.juanma.mcm3d.core.elevation.TileId
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Offline cache of Terrarium tiles. Downloaded once per zone, read from disk afterwards. */
class TerrariumTileStore(private val dir: File) {
  private fun file(tile: TileId) = File(dir, "${tile.zoom}/${tile.x}/${tile.y}.png")

  fun missing(tiles: Collection<TileId>): List<TileId> = tiles.filter { file(it).length() == 0L }

  /** Blocking; throws [IOException] when the network fails so the caller can retry later. */
  fun download(tile: TileId) {
    val connection = URL(Terrarium.url(tile)).openConnection() as HttpURLConnection
    try {
      connection.connectTimeout = CONNECT_TIMEOUT_MS
      connection.readTimeout = READ_TIMEOUT_MS
      connection.setRequestProperty("User-Agent", "MCM3D/${BuildConfig.VERSION_NAME} (Android)")
      val code = connection.responseCode
      if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code for $tile")
      val bytes = connection.inputStream.use(::readLimited)
      if (!isPng(bytes)) throw IOException("Not a PNG: $tile")
      val target = file(tile)
      target.parentFile?.mkdirs()
      val temp = File(target.path + ".tmp")
      temp.writeBytes(bytes)
      if (!temp.renameTo(target)) throw IOException("Cannot store $tile")
    } finally {
      connection.disconnect()
    }
  }

  fun pixels(tile: TileId): IntArray? {
    val source = file(tile)
    if (!source.exists()) return null
    val options = BitmapFactory.Options().apply {
      inPreferredConfig = Bitmap.Config.ARGB_8888
      inPremultiplied = false
      inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
    }
    val bitmap = BitmapFactory.decodeFile(source.path, options)
    if (bitmap == null) {
      source.delete()
      return null
    }
    try {
      val size = Terrarium.TILE_SIZE
      if (bitmap.width != size || bitmap.height != size) return null
      val pixels = IntArray(size * size)
      bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
      return pixels
    } finally {
      bitmap.recycle()
    }
  }

  private fun readLimited(input: InputStream): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    while (true) {
      val n = input.read(buffer)
      if (n < 0) break
      out.write(buffer, 0, n)
      if (out.size() > MAX_TILE_BYTES) throw IOException("Tile too large")
    }
    return out.toByteArray()
  }

  private fun isPng(bytes: ByteArray): Boolean =
    bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
      bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte()

  private companion object {
    const val CONNECT_TIMEOUT_MS = 15_000
    const val READ_TIMEOUT_MS = 30_000
    const val MAX_TILE_BYTES = 2 * 1024 * 1024
  }
}
