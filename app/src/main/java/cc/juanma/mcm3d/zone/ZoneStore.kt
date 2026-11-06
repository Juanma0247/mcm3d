package cc.juanma.mcm3d.zone

import android.util.Xml
import cc.juanma.mcm3d.BuildConfig
import cc.juanma.mcm3d.core.zone.OsmWay
import cc.juanma.mcm3d.core.zone.OverpassException
import cc.juanma.mcm3d.core.zone.OverpassParser
import cc.juanma.mcm3d.core.zone.ZoneCell
import cc.juanma.mcm3d.core.zone.ZoneCodec
import cc.juanma.mcm3d.core.zone.overpassQuery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * OpenStreetMap highways stored per grid cell for offline use. Cells are downloaded once from
 * Overpass and kept on disk; a small in-memory cache serves the map while it pans.
 */
class ZoneStore(private val dir: File) {
  private val cache = object : LinkedHashMap<ZoneCell, List<OsmWay>>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ZoneCell, List<OsmWay>>?) = size > CACHE_CELLS
  }

  private val _version = MutableStateFlow(0)

  /** Increases whenever a cell is stored, so maps reload what they show. */
  val version: StateFlow<Int> = _version.asStateFlow()

  private fun file(cell: ZoneCell) = File(dir, "${cell.key}.bin")

  fun has(cell: ZoneCell) = file(cell).exists()

  fun missing(cells: Collection<ZoneCell>) = cells.filterNot(::has)

  /** Blocking. Returns null when the cell has not been downloaded. */
  fun load(cell: ZoneCell): List<OsmWay>? {
    synchronized(cache) { cache[cell]?.let { return it } }
    val source = file(cell)
    if (!source.exists()) return null
    val ways = try {
      ZoneCodec.decode(source.readBytes())
    } catch (error: IllegalArgumentException) {
      source.delete()
      return null
    }
    synchronized(cache) { cache[cell] = ways }
    return ways
  }

  /** Blocking; throws [IOException] so the caller can retry later. */
  fun download(cell: ZoneCell) {
    val connection = URL(OVERPASS_URL).openConnection() as HttpURLConnection
    val ways = try {
      connection.requestMethod = "POST"
      connection.doOutput = true
      connection.connectTimeout = CONNECT_TIMEOUT_MS
      connection.readTimeout = READ_TIMEOUT_MS
      connection.setRequestProperty("User-Agent", "MCM3D/${BuildConfig.VERSION_NAME} (Android)")
      connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
      val body = "data=" + URLEncoder.encode(overpassQuery(cell.bounds), "UTF-8")
      connection.outputStream.use { it.write(body.toByteArray()) }
      val code = connection.responseCode
      if (code != HttpURLConnection.HTTP_OK) throw IOException("Overpass HTTP $code")
      connection.inputStream.buffered().use { input ->
        val parser = Xml.newPullParser()
        parser.setInput(input, "UTF-8")
        OverpassParser.parse(parser)
      }
    } catch (error: XmlPullParserException) {
      throw IOException("Overpass answer unreadable", error)
    } catch (error: OverpassException) {
      throw IOException(error.message, error)
    } finally {
      connection.disconnect()
    }
    dir.mkdirs()
    val target = file(cell)
    val temp = File(dir, "${cell.key}.tmp")
    temp.writeBytes(ZoneCodec.encode(ways))
    if (!temp.renameTo(target)) throw IOException("Cannot store zone ${cell.key}")
    synchronized(cache) { cache[cell] = ways }
    _version.value++
  }

  private companion object {
    const val OVERPASS_URL = "https://overpass-api.de/api/interpreter"
    const val CONNECT_TIMEOUT_MS = 20_000
    const val READ_TIMEOUT_MS = 120_000
    const val CACHE_CELLS = 24
  }
}
