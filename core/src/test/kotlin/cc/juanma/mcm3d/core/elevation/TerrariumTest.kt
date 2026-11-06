package cc.juanma.mcm3d.core.elevation

import cc.juanma.mcm3d.core.route.Bounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerrariumTest {
  private fun argb(elevation: Double): Int {
    val v = elevation + 32768.0
    val r = (v / 256).toInt()
    val g = (v - r * 256).toInt()
    val b = ((v - r * 256 - g) * 256).toInt()
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
  }

  @Test
  fun decodesTerrariumPixels() {
    assertEquals(0.0, Terrarium.decode(0xFF800000.toInt()), 1e-9)
    assertEquals(2031.5, Terrarium.decode(argb(2031.5)), 1e-2)
    assertEquals(-50.0, Terrarium.decode(argb(-50.0)), 1e-2)
  }

  @Test
  fun tileMathMatchesWebMercator() {
    assertEquals(0.0, Terrarium.tileX(-180.0, 13), 1e-9)
    assertEquals(4096.0, Terrarium.tileX(0.0, 13), 1e-9)
    assertEquals(4096.0, Terrarium.tileY(0.0, 13), 1e-9)
    val tiles = Terrarium.tilesCovering(Bounds(5.0, -75.45, 5.05, -75.40), 13)
    assertTrue(tiles.size in 1..6)
    assertTrue(tiles.all { it.zoom == 13 })
  }

  @Test
  fun samplerInterpolatesAcrossTileBorder() {
    val size = Terrarium.TILE_SIZE
    val left = IntArray(size * size) { argb(1000.0) }
    val right = IntArray(size * size) { argb(1100.0) }
    val sampler = ElevationSampler(zoom = 1) { tile ->
      when {
        tile.y != 0 -> null
        tile.x == 0 -> left
        tile.x == 1 -> right
        else -> null
      }
    }
    assertEquals(1000.0, sampler.elevationAt(60.0, -90.0), 0.1)
    assertEquals(1050.0, sampler.elevationAt(60.0, 0.0), 0.1)
    assertTrue(sampler.elevationAt(-60.0, 0.0).isNaN())
  }
}
