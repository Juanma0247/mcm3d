package cc.juanma.mcm3d.core.route

import org.xmlpull.v1.XmlPullParser

/**
 * Reads tracks, routes and waypoints from GPX 1.0 and 1.1. All track segments are joined in file
 * order; a `<rte>` is used only when there is no track. Times are ignored: planned routes carry
 * synthetic ones.
 */
internal object GpxParser {
  private val POINT_TAGS = setOf("trkpt", "rtept", "wpt")

  fun parse(parser: XmlPullParser): ParsedRoute {
    val stack = arrayListOf(localName(parser.name))
    val trackPoints = ArrayList<TrackPoint>()
    val routePoints = ArrayList<TrackPoint>()
    val waypoints = ArrayList<Waypoint>()
    var metadataName: String? = null
    var trackName: String? = null
    var routeName: String? = null

    var lat = Double.NaN
    var lon = Double.NaN
    var elevation = Double.NaN
    var pointName: String? = null
    var pointDescription: String? = null

    while (stack.isNotEmpty()) {
      when (parser.next()) {
        XmlPullParser.END_DOCUMENT -> throw RouteParseException(RouteParseException.Reason.MALFORMED)
        XmlPullParser.START_TAG -> {
          val name = localName(parser.name)
          val parent = stack.last()
          when {
            name == "ele" && parent in POINT_TAGS -> elevation = parser.textOrNull()?.toDoubleOrNull() ?: Double.NaN
            name == "name" -> {
              val text = parser.textOrNull()
              when (parent) {
                "metadata" -> metadataName = metadataName ?: text
                "trk" -> trackName = trackName ?: text
                "rte" -> routeName = routeName ?: text
                "wpt" -> pointName = text
              }
            }
            (name == "desc" || name == "cmt") && parent == "wpt" ->
              pointDescription = pointDescription ?: plainText(parser.nextText())
            else -> {
              if (name in POINT_TAGS) {
                lat = parser.doubleAttribute("lat")
                lon = parser.doubleAttribute("lon")
                elevation = Double.NaN
                pointName = null
                pointDescription = null
              }
              stack.add(name)
            }
          }
        }
        XmlPullParser.END_TAG -> {
          val closed = stack.removeAt(stack.lastIndex)
          if (closed in POINT_TAGS && isValidCoordinate(lat, lon)) {
            when (closed) {
              "trkpt" -> trackPoints.add(TrackPoint(lat, lon, elevation))
              "rtept" -> routePoints.add(TrackPoint(lat, lon, elevation))
              "wpt" -> waypoints.add(Waypoint(pointName.orEmpty(), lat, lon, elevation, pointDescription))
            }
          }
        }
      }
    }

    return ParsedRoute(
      name = metadataName ?: trackName ?: routeName,
      points = if (trackPoints.size >= 2) trackPoints else routePoints,
      waypoints = waypoints,
      format = RouteFormat.GPX,
    )
  }
}
