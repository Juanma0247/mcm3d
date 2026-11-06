package cc.juanma.mcm3d.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Database(entities = [RouteEntity::class, WaypointEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
  abstract fun routes(): RouteDao
}

@Dao
abstract class RouteDao {
  @Query("SELECT * FROM routes ORDER BY createdAtMs DESC")
  abstract fun observeAll(): Flow<List<RouteEntity>>

  @Query("SELECT * FROM routes WHERE id = :id")
  abstract fun observe(id: Long): Flow<RouteEntity?>

  @Query("SELECT * FROM routes WHERE id = :id")
  abstract suspend fun get(id: Long): RouteEntity?

  @Query("SELECT * FROM routes WHERE recording = 1")
  abstract suspend fun recording(): List<RouteEntity>

  @Query("SELECT * FROM waypoints WHERE routeId = :routeId ORDER BY position")
  abstract fun observeWaypoints(routeId: Long): Flow<List<WaypointEntity>>

  @Query("SELECT * FROM waypoints WHERE routeId = :routeId ORDER BY position")
  abstract suspend fun waypoints(routeId: Long): List<WaypointEntity>

  @Query("SELECT COUNT(*) FROM waypoints WHERE routeId = :routeId")
  abstract suspend fun waypointCount(routeId: Long): Int

  @Insert
  abstract suspend fun insertRoute(route: RouteEntity): Long

  @Insert
  abstract suspend fun insertWaypoints(waypoints: List<WaypointEntity>)

  @Update
  abstract suspend fun update(route: RouteEntity)

  @Update
  abstract suspend fun updateWaypoints(waypoints: List<WaypointEntity>)

  @Query("UPDATE routes SET name = :name WHERE id = :id")
  abstract suspend fun rename(id: Long, name: String)

  @Query("DELETE FROM routes WHERE id = :id")
  protected abstract suspend fun deleteRoute(id: Long)

  @Query("DELETE FROM waypoints WHERE routeId = :routeId")
  protected abstract suspend fun deleteWaypoints(routeId: Long)

  @Transaction
  open suspend fun delete(id: Long) {
    deleteWaypoints(id)
    deleteRoute(id)
  }

  @Transaction
  open suspend fun insertWithWaypoints(route: RouteEntity, waypoints: List<WaypointEntity>): Long {
    val id = insertRoute(route)
    if (waypoints.isNotEmpty()) insertWaypoints(waypoints.map { it.copy(routeId = id) })
    return id
  }

  @Transaction
  open suspend fun appendWaypoint(waypoint: WaypointEntity) {
    insertWaypoints(listOf(waypoint.copy(position = waypointCount(waypoint.routeId))))
  }
}
