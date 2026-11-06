package cc.juanma.mcm3d.core.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

class SessionCsvTest {
  @Test
  fun readsFixesWithFusedAltitudeAndMarks() {
    val csv = """
      # MCM3D session format 2
      # start=2026-01-01T08:00:00-05:00 start_elapsed_ns=1
      # FIX,t_ms,lat,lon,...
      MODE,0,moving,start,0
      BARO,0,795.000
      FIX,1000,5.0000000,-75.4000000,3.8,2100.0,2075.0,2.5,1.2,0.5,10.0,30.0,30,40,20,40.0,0
      SAT,1000,1,10,28.3,56,13,0,1575.420
      FIX,2000,5.0001000,-75.4000000,45.0,2100.0,2075.0,2.5,1.2,0.5,10.0,30.0,30,40,20,40.0,2
      FIX,3000,5.0002000,-75.4000000,3.8,2100.0,2075.0,2.5,1.2,0.5,10.0,30.0,30,40,20,40.0,4
      MARK,3500,1,5
      END,4000,3,1
    """.trimIndent()
    val track = SessionCsv.read(BufferedReader(StringReader(csv)))
    val start = 1767272400000L
    assertEquals(start, track.startTimeMs)
    assertEquals(2, track.points.size)
    assertEquals(start + 3000, track.points[1].timeMs)
    assertEquals(2075.0, track.points[0].elevation, 0.5)
    assertTrue(track.points.all { it.hasElevation })
    assertEquals(start + 3500, track.marks.single().timeMs)
  }
}
