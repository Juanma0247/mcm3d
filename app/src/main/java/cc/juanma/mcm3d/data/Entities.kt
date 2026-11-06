package cc.juanma.mcm3d.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class RouteSource { IMPORTED, RECORDED }

/** Where the elevations of a route come from. */
enum class ElevationSource {
  /** Imported; waiting for the elevation model (needs a connection once). */
  PENDING,

  /** Recalculated from the elevation model and smoothed. */
  MODEL,

  /** Kept from the imported file because the model could not be used. */
  FILE,

  /** Measured while recording: barometer fused with GNSS. */
  RECORDED,

  NONE,
}

@Entity(tableName = "routes")
data class RouteEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val source: RouteSource,
  val fileFormat: String? = null,
  val createdAtMs: Long,
  val trackUpdatedAtMs: Long,
  val recording: Boolean = false,
  val pointCount: Int = 0,
  val distanceM: Double = 0.0,
  val ascentM: Double? = null,
  val descentM: Double? = null,
  val minElevationM: Double? = null,
  val maxElevationM: Double? = null,
  val durationMs: Long? = null,
  val elevationSource: ElevationSource,
  val minLat: Double = 0.0,
  val minLon: Double = 0.0,
  val maxLat: Double = 0.0,
  val maxLon: Double = 0.0,
)

@Entity(tableName = "waypoints", indices = [Index("routeId")])
data class WaypointEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val routeId: Long,
  val position: Int,
  val name: String,
  val lat: Double,
  val lon: Double,
  val elevationM: Double?,
  val description: String?,
)
