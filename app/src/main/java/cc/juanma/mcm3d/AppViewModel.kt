package cc.juanma.mcm3d

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cc.juanma.mcm3d.importing.RouteImporter
import cc.juanma.mcm3d.recording.OutingStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface Screen {
  data object Routes : Screen
  data class RouteDetail(val routeId: Long) : Screen
  data object Outing : Screen
  data object Diagnostics : Screen
  data object SessionImport : Screen
}

class UiMessage(@StringRes val text: Int)

/** Activity-wide state: the navigation back stack, imports and one-off messages. */
class AppViewModel(application: Application) : AndroidViewModel(application) {
  private val graph = application.appGraph

  val backStack = mutableStateListOf<Screen>(Screen.Routes)

  private val _messages = Channel<UiMessage>(Channel.BUFFERED)
  val messages: Flow<UiMessage> = _messages.receiveAsFlow()

  private val _importing = MutableStateFlow(false)
  val importing: StateFlow<Boolean> = _importing.asStateFlow()

  init {
    viewModelScope.launch {
      if (!OutingStatus.state.value.active) graph.routes.recoverInterruptedRecordings(activeRouteId = null)
    }
  }

  fun open(screen: Screen) {
    if (backStack.lastOrNull() != screen) backStack.add(screen)
  }

  fun back() {
    if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
  }

  fun replaceTop(screen: Screen) {
    backStack[backStack.lastIndex] = screen
  }

  /** Removes a screen wherever it is in the stack, for screens whose content disappeared. */
  fun close(screen: Screen) {
    if (backStack.size > 1) backStack.remove(screen)
  }

  fun replace(old: Screen, new: Screen) {
    val index = backStack.indexOf(old)
    if (index >= 0) backStack[index] = new
  }

  fun openOuting() {
    backStack.removeAll { it == Screen.Outing }
    backStack.add(Screen.Outing)
  }

  fun show(@StringRes text: Int) {
    _messages.trySend(UiMessage(text))
  }

  fun importRoute(uri: Uri) {
    if (_importing.value) return
    _importing.value = true
    viewModelScope.launch {
      val defaultName = getApplication<Application>().getString(R.string.import_default_name)
      when (val result = graph.importer.import(uri, defaultName)) {
        is RouteImporter.Result.Success -> {
          backStack.removeAll { it is Screen.RouteDetail }
          open(Screen.RouteDetail(result.routeId))
          show(R.string.import_done)
        }
        is RouteImporter.Result.Failure -> show(
          when (result.reason) {
            RouteImporter.FailureReason.UNREADABLE -> R.string.import_error_unreadable
            RouteImporter.FailureReason.NOT_A_ROUTE -> R.string.import_error_not_a_route
            RouteImporter.FailureReason.NO_TRACK -> R.string.import_error_no_track
            RouteImporter.FailureReason.MALFORMED -> R.string.import_error_malformed
            RouteImporter.FailureReason.TOO_LARGE -> R.string.import_error_too_large
          },
        )
      }
      _importing.value = false
    }
  }
}
