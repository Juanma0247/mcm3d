package cc.juanma.mcm3d

import android.app.Application
import android.content.Context
import androidx.room.Room
import cc.juanma.mcm3d.data.AppDatabase
import cc.juanma.mcm3d.data.RouteRepository
import cc.juanma.mcm3d.data.TrackStore
import cc.juanma.mcm3d.elevation.TerrariumTileStore
import cc.juanma.mcm3d.importing.RouteImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

class Mcm3dApplication : Application() {
  val graph: AppGraph by lazy { AppGraph(this) }
}

/** Manual dependency graph: a handful of singletons, cheaper to build and start than a DI framework. */
class AppGraph(context: Context) {
  private val appContext = context.applicationContext

  /** Work that must outlive a screen or the recording service, such as finishing an outing. */
  val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private val database: AppDatabase by lazy {
    Room.databaseBuilder(appContext, AppDatabase::class.java, "mcm3d.db").build()
  }

  val routes: RouteRepository by lazy {
    RouteRepository(
      context = appContext,
      dao = database.routes(),
      tracks = TrackStore(File(appContext.filesDir, "tracks")),
      tiles = TerrariumTileStore(File(appContext.filesDir, "elevation")),
    )
  }

  val importer: RouteImporter by lazy { RouteImporter(appContext, routes) }
}

val Context.appGraph: AppGraph get() = (applicationContext as Mcm3dApplication).graph
