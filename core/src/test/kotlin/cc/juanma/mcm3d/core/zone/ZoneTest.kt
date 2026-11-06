package cc.juanma.mcm3d.core.zone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.StringReader

class ZoneTest {
  private fun parse(xml: String): List<OsmWay> {
    val parser = KXmlParser()
    parser.setInput(StringReader(xml))
    return OverpassParser.parse(parser)
  }

  @Test
  fun classifiesHighways() {
    assertEquals(WayClass.MAJOR, classifyHighway("secondary_link"))
    assertEquals(WayClass.MINOR, classifyHighway("residential"))
    assertEquals(WayClass.TRACK, classifyHighway("track"))
    assertEquals(WayClass.PATH, classifyHighway("steps"))
    assertNull(classifyHighway("construction"))
  }

  @Test
  fun parsesOverpassGeometry() {
    val ways = parse(
      """
      <osm version="0.6">
        <way id="1"><bounds minlat="5" minlon="-75" maxlat="5.1" maxlon="-74.9"/>
          <nd ref="1" lat="5.0" lon="-75.0"/><nd ref="2" lat="5.001" lon="-75.001"/>
          <tag k="name" v="Calle"/><tag k="highway" v="path"/>
        </way>
        <way id="2"><nd ref="3" lat="5.0" lon="-75.0"/><nd ref="4" lat="5.1" lon="-75.0"/><tag k="highway" v="proposed"/></way>
        <way id="3"><nd ref="5" lat="5.0" lon="-75.0"/><tag k="highway" v="primary"/></way>
      </osm>
      """.trimIndent(),
    )
    assertEquals(1, ways.size)
    assertEquals(WayClass.PATH, ways[0].wayClass)
    assertEquals(5.001, ways[0].lats[1], 1e-9)
  }

  @Test
  fun overpassErrorsAreReported() {
    try {
      parse("""<osm><remark> runtime error: Query timed out </remark></osm>""")
      fail("Expected an error")
    } catch (error: OverpassException) {
      assertTrue(error.message!!.contains("timed out"))
    }
  }

  @Test
  fun codecRoundTrips() {
    val ways = listOf(
      OsmWay(WayClass.MINOR, doubleArrayOf(5.0612345, 5.0612999), doubleArrayOf(-75.4987654, -75.49)),
      OsmWay(WayClass.TRACK, doubleArrayOf(5.0, 5.1, 5.2), doubleArrayOf(-75.0, -75.1, -75.2)),
    )
    val back = ZoneCodec.decode(ZoneCodec.encode(ways))
    assertEquals(2, back.size)
    assertEquals(WayClass.TRACK, back[1].wayClass)
    assertEquals(5.0612345, back[0].lats[0], 1e-7)
    assertEquals(-75.2, back[1].lons[2], 1e-7)
  }

  @Test
  fun gridCoversARadius() {
    val cell = ZoneGrid.cellOf(5.07, -75.51)
    assertTrue(cell.bounds.minLat <= 5.07 && cell.bounds.maxLat > 5.07)
    assertTrue(cell.bounds.minLon <= -75.51 && cell.bounds.maxLon > -75.51)
    val around = ZoneGrid.cellsAround(5.07, -75.51, 1_500.0)
    assertTrue(around.size in 4..9)
    assertTrue(cell in around)
  }
}
