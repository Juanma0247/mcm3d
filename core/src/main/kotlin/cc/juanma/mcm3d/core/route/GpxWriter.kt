package cc.juanma.mcm3d.core.route

import java.time.Instant
import java.util.Locale

object GpxWriter {
  fun write(
    out: Appendable,
    name: String,
    points: List<TrackPoint>,
    waypoints: List<Waypoint>,
    creator: String,
  ) {
    out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    out.append("<gpx version=\"1.1\" creator=\"").append(escape(creator)).append("\"")
    out.append(" xmlns=\"http://www.topografix.com/GPX/1/1\"")
    out.append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"")
    out.append(" xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">\n")
    out.append("  <metadata>\n    <name>").append(escape(name)).append("</name>\n")
    points.firstOrNull { it.hasTime }?.let { out.append("    <time>").append(time(it.timeMs)).append("</time>\n") }
    out.append("  </metadata>\n")
    for (waypoint in waypoints) {
      out.append("  <wpt lat=\"").append(coordinate(waypoint.lat)).append("\" lon=\"").append(coordinate(waypoint.lon)).append("\">\n")
      if (!waypoint.elevation.isNaN()) out.append("    <ele>").append(elevation(waypoint.elevation)).append("</ele>\n")
      if (waypoint.name.isNotBlank()) out.append("    <name>").append(escape(waypoint.name)).append("</name>\n")
      waypoint.description?.let { out.append("    <desc>").append(escape(it)).append("</desc>\n") }
      out.append("  </wpt>\n")
    }
    out.append("  <trk>\n    <name>").append(escape(name)).append("</name>\n    <trkseg>\n")
    for (point in points) {
      out.append("      <trkpt lat=\"").append(coordinate(point.lat)).append("\" lon=\"").append(coordinate(point.lon)).append("\">")
      if (point.hasElevation) out.append("<ele>").append(elevation(point.elevation)).append("</ele>")
      if (point.hasTime) out.append("<time>").append(time(point.timeMs)).append("</time>")
      out.append("</trkpt>\n")
    }
    out.append("    </trkseg>\n  </trk>\n</gpx>\n")
  }

  private fun coordinate(value: Double) = String.format(Locale.ROOT, "%.7f", value)

  private fun elevation(value: Double) = String.format(Locale.ROOT, "%.1f", value)

  private fun time(ms: Long) = Instant.ofEpochMilli(ms).toString()

  private fun escape(text: String): String = buildString(text.length) {
    for (c in text) {
      when (c) {
        '&' -> append("&amp;")
        '<' -> append("&lt;")
        '>' -> append("&gt;")
        '"' -> append("&quot;")
        '\'' -> append("&apos;")
        else -> if (c >= ' ' || c == '\n' || c == '\t') append(c)
      }
    }
  }
}
