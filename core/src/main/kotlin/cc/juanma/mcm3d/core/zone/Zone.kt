package cc.juanma.mcm3d.core.zone

import cc.juanma.mcm3d.core.route.Bounds
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** Drawing classes for OpenStreetMap highways: what a walker needs to tell apart, nothing more. */
enum class WayClass { MAJOR, MINOR, TRACK, PATH }

class OsmWay(val wayClass: WayClass, val lats: DoubleArray, val lons: DoubleArray) {
  val minLat = lats.min()
  val maxLat = lats.max()
  val minLon = lons.min()
  val maxLon = lons.max()

  fun intersects(bounds: Bounds): Boolean =
    maxLat >= bounds.minLat && minLat <= bounds.maxLat && maxLon >= bounds.minLon && minLon <= bounds.maxLon
}

fun classifyHighway(value: String): WayClass? = when (value.removeSuffix("_link")) {
  "motorway", "trunk", "primary", "secondary", "tertiary" -> WayClass.MAJOR
  "residential", "unclassified", "living_street", "service", "road" -> WayClass.MINOR
  "track" -> WayClass.TRACK
  "footway", "path", "pedestrian", "steps", "cycleway", "bridleway" -> WayClass.PATH
  else -> null
}

data class ZoneCell(val x: Int, val y: Int) {
  val bounds: Bounds
    get() = Bounds(
      minLat = y * ZoneGrid.CELL_DEGREES - 90.0,
      minLon = x * ZoneGrid.CELL_DEGREES - 180.0,
      maxLat = (y + 1) * ZoneGrid.CELL_DEGREES - 90.0,
      maxLon = (x + 1) * ZoneGrid.CELL_DEGREES - 180.0,
    )

  val key: String get() = "${x}_$y"
}

/**
 * Fixed grid of about 2.2 km cells. Map data is downloaded and stored per cell, so overlapping
 * zones (around the user, around each route) never duplicate data.
 */
object ZoneGrid {
  const val CELL_DEGREES = 0.02

  fun cellOf(lat: Double, lon: Double) = ZoneCell(
    x = floor((lon + 180.0) / CELL_DEGREES).toInt(),
    y = floor((lat + 90.0) / CELL_DEGREES).toInt(),
  )

  fun cellsIn(bounds: Bounds): Set<ZoneCell> {
    val a = cellOf(bounds.minLat, bounds.minLon)
    val b = cellOf(bounds.maxLat, bounds.maxLon)
    return buildSet { for (y in a.y..b.y) for (x in a.x..b.x) add(ZoneCell(x, y)) }
  }

  fun cellsAround(lat: Double, lon: Double, radiusM: Double): Set<ZoneCell> =
    cellsIn(Bounds(lat, lon, lat, lon).expandedBy(radiusM))

  fun cellsAlong(lats: DoubleArray, lons: DoubleArray, marginM: Double): Set<ZoneCell> {
    val out = LinkedHashSet<ZoneCell>()
    if (lats.isEmpty()) return out
    val step = max(1, lats.size / 500)
    var i = 0
    while (i < lats.size) {
      out += cellsAround(lats[i], lons[i], marginM)
      i += step
    }
    out += cellsAround(lats.last(), lons.last(), marginM)
    return out
  }
}

/** Binary cell file: header (magic, version, way count), then per way its class, point count and 1e-7 degree coordinates. */
object ZoneCodec {
  private const val MAGIC = 0x4D434D5A
  private const val VERSION: Byte = 1
  private const val E7 = 1e7

  fun encode(ways: List<OsmWay>): ByteArray {
    val size = 12 + ways.sumOf { 5 + it.lats.size * 8 }
    val buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
    buffer.putInt(MAGIC).put(VERSION).put(0).put(0).put(0).putInt(ways.size)
    for (way in ways) {
      buffer.put(way.wayClass.ordinal.toByte()).putInt(way.lats.size)
      for (i in way.lats.indices) {
        buffer.putInt((way.lats[i] * E7).roundToInt())
        buffer.putInt((way.lons[i] * E7).roundToInt())
      }
    }
    return buffer.array()
  }

  fun decode(bytes: ByteArray): List<OsmWay> {
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    require(bytes.size >= 12 && buffer.getInt() == MAGIC) { "Not a zone file" }
    require(buffer.get() == VERSION) { "Unsupported zone version" }
    buffer.position(8)
    val count = buffer.getInt()
    val classes = WayClass.entries
    return List(count) {
      val wayClass = classes[buffer.get().toInt()]
      val n = buffer.getInt()
      val lats = DoubleArray(n)
      val lons = DoubleArray(n)
      for (i in 0 until n) {
        lats[i] = buffer.getInt() / E7
        lons[i] = buffer.getInt() / E7
      }
      OsmWay(wayClass, lats, lons)
    }
  }
}

/** Overpass QL for every walkable or drivable highway in a cell, with inline geometry. */
fun overpassQuery(bounds: Bounds): String {
  val box = "${bounds.minLat},${bounds.minLon},${bounds.maxLat},${bounds.maxLon}"
  return "[out:xml][timeout:90];way[\"highway\"](${box});out geom qt;"
}
