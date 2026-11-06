package cc.juanma.mcm3d.core.route

data class TrackPoint(
  val lat: Double,
  val lon: Double,
  val elevation: Double = Double.NaN,
  val timeMs: Long = NO_TIME,
) {
  val hasElevation: Boolean get() = !elevation.isNaN()
  val hasTime: Boolean get() = timeMs != NO_TIME

  companion object {
    const val NO_TIME = Long.MIN_VALUE
  }
}

data class Waypoint(
  val name: String,
  val lat: Double,
  val lon: Double,
  val elevation: Double = Double.NaN,
  val description: String? = null,
)

enum class RouteFormat { GPX, KML, KMZ }

data class ParsedRoute(
  val name: String?,
  val points: List<TrackPoint>,
  val waypoints: List<Waypoint>,
  val format: RouteFormat,
)

class RouteParseException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
  enum class Reason { NOT_A_ROUTE, NO_TRACK, MALFORMED, TOO_LARGE }
}

internal fun isValidCoordinate(lat: Double, lon: Double): Boolean =
  lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0 && !(lat == 0.0 && lon == 0.0)
