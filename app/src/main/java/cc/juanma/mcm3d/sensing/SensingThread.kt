package cc.juanma.mcm3d.sensing

import android.os.Handler
import android.os.HandlerThread
import androidx.core.os.ExecutorCompat
import java.util.concurrent.Executor

internal object SensingThread {
  private val thread = HandlerThread("mcm3d-sensing").apply { start() }
  val handler = Handler(thread.looper)
  val executor: Executor = ExecutorCompat.create(handler)
}
