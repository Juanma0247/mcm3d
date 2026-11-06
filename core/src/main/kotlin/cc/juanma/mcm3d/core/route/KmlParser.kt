package cc.juanma.mcm3d.core.route

import org.xmlpull.v1.XmlPullParser

/**
 * Reads KML 2.2: every `LineString` and `gx:Track` is joined in file order into one line, and
 * placemarks holding a single `Point` become waypoints. Polygons are ignored.
 */
internal object KmlParser {
  private val COMMA = Regex("\\s*,\\s*")
  private val SPACES = Regex("\\s+")

  fun parse(parser: XmlPullParser, format: RouteFormat): ParsedRoute {
    val stack = arrayListOf(localName(parser.name))
    val linePoints = ArrayList<TrackPoint>()
    val waypoints = ArrayList<Waypoint>()
    var documentName: String? = null
    var firstLineName: String? = null

    var placemarkName: String? = null
    var placemarkDescription: String? = null
    var placemarkPoint: TrackPoint? = null
    var placemarkHasLine = false

    while (stack.isNotEmpty()) {
      when (parser.next()) {
        XmlPullParser.END_DOCUMENT -> throw RouteParseException(RouteParseException.Reason.MALFORMED)
        XmlPullParser.START_TAG -> {
          val name = localName(parser.name)
          val parent = stack.last()
          when {
            name == "name" -> {
              val text = parser.textOrNull()
              when (parent) {
                "Document" -> documentName = documentName ?: text
                "Placemark" -> placemarkName = text
              }
            }
            name == "description" && parent == "Placemark" -> placemarkDescription = plainText(parser.nextText())
            name == "coordinates" -> {
              val tuples = parseTuples(parser.nextText())
              when (parent) {
                "Point" -> placemarkPoint = tuples.firstOrNull()
                "LineString" -> {
                  linePoints.addAll(tuples)
                  placemarkHasLine = placemarkHasLine || tuples.isNotEmpty()
                }
              }
            }
            name == "coord" && parent == "Track" -> {
              parseTrackCoord(parser.nextText())?.let {
                linePoints.add(it)
                placemarkHasLine = true
              }
            }
            else -> {
              if (name == "Placemark") {
                placemarkName = null
                placemarkDescription = null
                placemarkPoint = null
                placemarkHasLine = false
              }
              stack.add(name)
            }
          }
        }
        XmlPullParser.END_TAG -> {
          val closed = stack.removeAt(stack.lastIndex)
          if (closed == "Placemark") {
            val point = placemarkPoint
            if (placemarkHasLine) {
              if (firstLineName == null) firstLineName = placemarkName
            } else if (point != null) {
              waypoints.add(Waypoint(placemarkName.orEmpty(), point.lat, point.lon, point.elevation, placemarkDescription))
            }
          }
        }
      }
    }

    return ParsedRoute(
      name = documentName ?: firstLineName,
      points = linePoints,
      waypoints = waypoints,
      format = format,
    )
  }

  private fun parseTuples(text: String): List<TrackPoint> {
    val normalized = text.trim().replace(COMMA, ",")
    if (normalized.isEmpty()) return emptyList()
    return normalized.split(SPACES).mapNotNull { tuple ->
      val parts = tuple.split(',')
      if (parts.size < 2) return@mapNotNull null
      point(parts[0], parts[1], parts.getOrNull(2))
    }
  }

  private fun parseTrackCoord(text: String): TrackPoint? {
    val parts = text.trim().split(SPACES)
    if (parts.size < 2) return null
    return point(parts[0], parts[1], parts.getOrNull(2))
  }

  private fun point(lonText: String, latText: String, altText: String?): TrackPoint? {
    val lon = lonText.toDoubleOrNull() ?: return null
    val lat = latText.toDoubleOrNull() ?: return null
    if (!isValidCoordinate(lat, lon)) return null
    val altitude = altText?.toDoubleOrNull()?.takeIf { it != 0.0 } ?: Double.NaN
    return TrackPoint(lat, lon, altitude)
  }
}
