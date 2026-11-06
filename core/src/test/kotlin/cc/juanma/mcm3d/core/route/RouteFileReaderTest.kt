package cc.juanma.mcm3d.core.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RouteFileReaderTest {
  private fun read(text: String): ParsedRoute = RouteFileReader.read(text.toByteArray()) { KXmlParser() }

  private fun readBytes(bytes: ByteArray): ParsedRoute = RouteFileReader.read(bytes) { KXmlParser() }

  private fun expectFailure(reason: RouteParseException.Reason, block: () -> Unit) {
    try {
      block()
      fail("Expected $reason")
    } catch (error: RouteParseException) {
      assertEquals(reason, error.reason)
    }
  }

  @Test
  fun readsGpxTrackSegmentsWaypointsAndName() {
    val route = read(
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
        <metadata><name>Circular &amp; test</name><author><name>Someone</name></author></metadata>
        <wpt lat="5.01" lon="-75.41"><ele>2100.5</ele><name>Puente</name><desc>&lt;b&gt;Old&lt;/b&gt; bridge</desc></wpt>
        <trk>
          <name>Track name</name>
          <trkseg>
            <trkpt lat="5.000" lon="-75.400"><ele>2000</ele><time>2026-01-01T00:00:00Z</time></trkpt>
            <trkpt lat="5.001" lon="-75.400"><ele>2010</ele></trkpt>
          </trkseg>
          <trkseg>
            <trkpt lat="5.002" lon="-75.400"/>
            <trkpt lat="91" lon="-75.400"/>
          </trkseg>
        </trk>
      </gpx>
      """.trimIndent(),
    )
    assertEquals(RouteFormat.GPX, route.format)
    assertEquals("Circular & test", route.name)
    assertEquals(3, route.points.size)
    assertEquals(2010.0, route.points[1].elevation, 1e-9)
    assertTrue(route.points[2].elevation.isNaN())
    assertEquals(1, route.waypoints.size)
    assertEquals("Puente", route.waypoints[0].name)
    assertEquals(2100.5, route.waypoints[0].elevation, 1e-9)
    assertEquals("Old bridge", route.waypoints[0].description)
  }

  @Test
  fun fallsBackToGpxRouteAndTrackName() {
    val route = read(
      """
      <gpx version="1.0"><rte><name>Planned</name>
        <rtept lat="5.0" lon="-75.4"/><rtept lat="5.1" lon="-75.4"/>
      </rte></gpx>
      """.trimIndent(),
    )
    assertEquals("Planned", route.name)
    assertEquals(2, route.points.size)
  }

  @Test
  fun readsKmlLineStringsTracksAndPoints() {
    val route = read(
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <kml xmlns="http://www.opengis.net/kml/2.2" xmlns:gx="http://www.google.com/kml/ext/2.2">
        <Document>
          <name>Ruta KML</name>
          <Placemark><name>Track</name>
            <LineString><coordinates>
              -75.400,5.000,2000 -75.400, 5.001,2010
              -75.400,5.002
            </coordinates></LineString>
          </Placemark>
          <Placemark><gx:Track><when>2026-01-01T00:00:00Z</when><gx:coord>-75.400 5.003 2030</gx:coord></gx:Track></Placemark>
          <Placemark><name>Mirador</name><description><![CDATA[<p>Vista</p>]]></description>
            <Point><coordinates>-75.41,5.01,0</coordinates></Point>
          </Placemark>
        </Document>
      </kml>
      """.trimIndent(),
    )
    assertEquals(RouteFormat.KML, route.format)
    assertEquals("Ruta KML", route.name)
    assertEquals(4, route.points.size)
    assertEquals(2030.0, route.points[3].elevation, 1e-9)
    assertTrue(route.points[2].elevation.isNaN())
    assertEquals("Mirador", route.waypoints.single().name)
    assertEquals("Vista", route.waypoints.single().description)
    assertTrue(route.waypoints.single().elevation.isNaN())
  }

  @Test
  fun readsKmzArchive() {
    val kml = """<kml><Document><Placemark><name>Zipped</name><LineString>
      <coordinates>-75.4,5.0 -75.4,5.1</coordinates></LineString></Placemark></Document></kml>"""
    val bytes = ByteArrayOutputStream().also { out ->
      ZipOutputStream(out).use { zip ->
        zip.putNextEntry(ZipEntry("files/readme.txt"))
        zip.write("hello".toByteArray())
        zip.putNextEntry(ZipEntry("doc.kml"))
        zip.write(kml.toByteArray())
      }
    }.toByteArray()
    val route = readBytes(bytes)
    assertEquals(RouteFormat.KMZ, route.format)
    assertEquals("Zipped", route.name)
    assertEquals(2, route.points.size)
  }

  @Test
  fun rejectsOtherXmlEmptyRoutesAndEntities() {
    expectFailure(RouteParseException.Reason.NOT_A_ROUTE) { read("<html><body/></html>") }
    expectFailure(RouteParseException.Reason.NO_TRACK) { read("""<gpx><wpt lat="5" lon="-75"/></gpx>""") }
    expectFailure(RouteParseException.Reason.MALFORMED) { read("<gpx><trk>") }
    expectFailure(RouteParseException.Reason.MALFORMED) {
      read("""<?xml version="1.0"?><!DOCTYPE gpx [<!ENTITY a "aaaa">]><gpx>&a;</gpx>""")
    }
    expectFailure(RouteParseException.Reason.NOT_A_ROUTE) { readBytes(byteArrayOf(0x50, 0x4B, 3, 4, 0, 0)) }
  }

  @Test
  fun writerOutputReadsBack() {
    val points = listOf(
      TrackPoint(5.0, -75.4, 2000.0, 1_700_000_000_000),
      TrackPoint(5.001, -75.4, Double.NaN, 1_700_000_001_000),
    )
    val text = StringBuilder().also {
      GpxWriter.write(it, "Salida <1>", points, listOf(Waypoint("Punto \"A\"", 5.0005, -75.4, 2005.0)), "MCM3D test")
    }.toString()
    assertTrue(text.contains("<time>2023-11-14T22:13:20Z</time>"))
    val route = read(text)
    assertEquals("Salida <1>", route.name)
    assertEquals(2, route.points.size)
    assertEquals(5.001, route.points[1].lat, 1e-9)
    assertEquals("Punto \"A\"", route.waypoints.single().name)
  }
}
