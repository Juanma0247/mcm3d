package cc.juanma.mcm3d.core.elevation

import cc.juanma.mcm3d.core.route.Bounds
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

data class TileId(val zoom: Int, val x: Int, val y: Int)

/**
 * Terrain Tiles in Terrarium encoding (AWS Open Data): 256 px Web Mercator PNG tiles where
 * elevation = R * 256 + G + B / 256 - 32768 meters. Zoom 13 gives about 19 m per pixel at the
 * latitude of Manizales, finer than the 30 m SRTM data behind it.
 */
object Terrarium {
  const val TILE_SIZE = 256
  const val ZOOM = 13

  fun url(tile: TileId): String =
    "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/${tile.zoom}/${tile.x}/${tile.y}.png"

  fun decode(argb: Int): Double {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return r * 256.0 + g + b / 256.0 - 32768.0
  }

  fun tileX(lon: Double, zoom: Int): Double = (lon + 180.0) / 360.0 * (1 shl zoom)

  fun tileY(lat: Double, zoom: Int): Double {
    val rad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
    return (1.0 - ln(tan(rad) + 1.0 / kotlin.math.cos(rad)) / PI) / 2.0 * (1 shl zoom)
  }

  fun tilesCovering(bounds: Bounds, zoom: Int = ZOOM): List<TileId> {
    val max = (1 shl zoom) - 1
    val x0 = floor(tileX(bounds.minLon, zoom)).toInt().coerceIn(0, max)
    val x1 = floor(tileX(bounds.maxLon, zoom)).toInt().coerceIn(0, max)
    val y0 = floor(tileY(bounds.maxLat, zoom)).toInt().coerceIn(0, max)
    val y1 = floor(tileY(bounds.minLat, zoom)).toInt().coerceIn(0, max)
    return buildList {
      for (y in y0..y1) for (x in x0..x1) add(TileId(zoom, x, y))
    }
  }
}

/** Bilinear sampling across tile borders. [pixels] returns a tile's ARGB pixels, or null when it is missing. */
class ElevationSampler(private val zoom: Int = Terrarium.ZOOM, private val pixels: (TileId) -> IntArray?) {
  private val cache = HashMap<TileId, IntArray?>()

  fun elevationAt(lat: Double, lon: Double): Double {
    val px = Terrarium.tileX(lon, zoom) * Terrarium.TILE_SIZE - 0.5
    val py = Terrarium.tileY(lat, zoom) * Terrarium.TILE_SIZE - 0.5
    val x0 = floor(px).toInt()
    val y0 = floor(py).toInt()
    val fx = px - x0
    val fy = py - y0
    val e00 = pixel(x0, y0)
    val e10 = pixel(x0 + 1, y0)
    val e01 = pixel(x0, y0 + 1)
    val e11 = pixel(x0 + 1, y0 + 1)
    if (e00.isNaN() || e10.isNaN() || e01.isNaN() || e11.isNaN()) return Double.NaN
    val top = e00 + (e10 - e00) * fx
    val bottom = e01 + (e11 - e01) * fx
    return top + (bottom - top) * fy
  }

  private fun pixel(globalX: Int, globalY: Int): Double {
    val size = Terrarium.TILE_SIZE
    val max = (1 shl zoom) * size - 1
    val x = globalX.coerceIn(0, max)
    val y = globalY.coerceIn(0, max)
    val tile = TileId(zoom, x / size, y / size)
    val data = cache.getOrPut(tile) { pixels(tile) } ?: return Double.NaN
    return Terrarium.decode(data[(y % size) * size + (x % size)])
  }
}
