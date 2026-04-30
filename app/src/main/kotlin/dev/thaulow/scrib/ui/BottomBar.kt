package dev.thaulow.scrib.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TOP_ROW_SIZE = 6
private const val BOTTOM_ROW_SIZE = 6
private const val TOTAL_BUTTONS = TOP_ROW_SIZE + BOTTOM_ROW_SIZE
private const val CHIP_SHOW_DELAY_MS = 200L

private val ROW_INDICES: List<Int> =
  List(TOP_ROW_SIZE) { 0 } + List(BOTTOM_ROW_SIZE) { 1 }

@Composable
fun BottomBar(
  imeVisible: Boolean,
  canUndo: Boolean,
  canRedo: Boolean,
  onToggleKeyboard: () -> Unit,
  onClear: () -> Unit,
  onCopy: () -> Unit,
  onReplace: () -> Unit,
  onAppend: () -> Unit,
  onUndo: () -> Unit,
  onRedo: () -> Unit,
  onMoveLeft: () -> Unit,
  onMoveRight: () -> Unit,
  onMoveUp: () -> Unit,
  onMoveDown: () -> Unit,
  onSelectWord: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()

  val interactionSources =
    remember { List(TOTAL_BUTTONS) { MutableInteractionSource() } }
  val bounds = remember { BarBoundsState(ROW_INDICES) }

  val specs: List<BarButtonSpec> =
    listOf(
      BarButtonSpec(
        label = "Append clipboard",
        contentDescription = "Append clipboard to note",
        enabled = true,
        onInvoke = onAppend,
        icon = Icons.Filled.PostAdd,
        interactionSource = interactionSources[0],
      ),
      BarButtonSpec(
        label = "Replace with clipboard",
        contentDescription = "Replace note with clipboard",
        enabled = true,
        onInvoke = onReplace,
        icon = Icons.Filled.ContentPaste,
        interactionSource = interactionSources[1],
      ),
      BarButtonSpec(
        label = "Copy to clipboard",
        contentDescription = "Copy note to clipboard",
        enabled = true,
        onInvoke = onCopy,
        icon = Icons.Filled.ContentCopy,
        interactionSource = interactionSources[2],
      ),
      BarButtonSpec(
        label = "Select word",
        contentDescription = "Select word",
        enabled = true,
        onInvoke = onSelectWord,
        icon = Icons.Filled.SelectAll,
        interactionSource = interactionSources[3],
      ),
      BarButtonSpec(
        label = "Up",
        contentDescription = "Move cursor up",
        enabled = true,
        onInvoke = onMoveUp,
        icon = Icons.Filled.KeyboardArrowUp,
        interactionSource = interactionSources[4],
      ),
      BarButtonSpec(
        label = if (imeVisible) "Hide keyboard" else "Show keyboard",
        contentDescription = if (imeVisible) "Hide keyboard" else "Show keyboard",
        enabled = true,
        onInvoke = onToggleKeyboard,
        icon = if (imeVisible) Icons.Filled.KeyboardHide else Icons.Filled.Keyboard,
        interactionSource = interactionSources[5],
      ),
      BarButtonSpec(
        label = "Clear",
        contentDescription = "Clear note",
        enabled = true,
        onInvoke = onClear,
        icon = Icons.Filled.Delete,
        interactionSource = interactionSources[6],
      ),
      BarButtonSpec(
        label = "Undo",
        contentDescription = "Undo",
        enabled = canUndo,
        onInvoke = onUndo,
        icon = Icons.AutoMirrored.Filled.Undo,
        interactionSource = interactionSources[7],
      ),
      BarButtonSpec(
        label = "Redo",
        contentDescription = "Redo",
        enabled = canRedo,
        onInvoke = onRedo,
        icon = Icons.AutoMirrored.Filled.Redo,
        interactionSource = interactionSources[8],
      ),
      BarButtonSpec(
        label = "Left",
        contentDescription = "Move cursor left",
        enabled = true,
        onInvoke = onMoveLeft,
        icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
        interactionSource = interactionSources[9],
      ),
      BarButtonSpec(
        label = "Down",
        contentDescription = "Move cursor down",
        enabled = true,
        onInvoke = onMoveDown,
        icon = Icons.Filled.KeyboardArrowDown,
        interactionSource = interactionSources[10],
      ),
      BarButtonSpec(
        label = "Right",
        contentDescription = "Move cursor right",
        enabled = true,
        onInvoke = onMoveRight,
        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        interactionSource = interactionSources[11],
      ),
    )

  val specsRef by rememberUpdatedState(specs)
  var armedIdx by remember { mutableStateOf<Int?>(null) }

  Box(modifier = modifier.fillMaxWidth()) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .onGloballyPositioned { coords -> bounds.reportBar(coords) }
          .pointerInput(Unit) {
            awaitEachGesture {
              val down = awaitFirstDown(requireUnconsumed = false)

              fun hitTest(pos: Offset): Int? {
                val zones =
                  computeArmedZones(
                    bounds.tightBounds.toList(),
                    bounds.rowIndex,
                    bounds.barWidthPx.toFloat(),
                  ) ?: return null
                val idx = zones.indexOfFirst { it.contains(pos) }
                return if (idx >= 0) idx else null
              }

              var current: Int? = null
              val pressByIdx = HashMap<Int, CompletableDeferred<Boolean>>()
              var chipShown = false
              var pendingShow: Job? = null

              fun startPress(idx: Int?) {
                if (idx == null) return
                val s = specsRef[idx]
                if (!s.enabled) return
                val press = PressInteraction.Press(Offset.Unspecified)
                val end = CompletableDeferred<Boolean>()
                pressByIdx[idx] = end
                scope.launch {
                  s.interactionSource.emit(press)
                  val release = end.await()
                  s.interactionSource.emit(
                    if (release) PressInteraction.Release(press) else PressInteraction.Cancel(press),
                  )
                }
              }

              fun endPress(
                idx: Int?,
                cancel: Boolean,
              ) {
                if (idx == null) return
                val end = pressByIdx.remove(idx) ?: return
                end.complete(!cancel)
              }

              try {
                val initial = hitTest(down.position)
                current = initial
                startPress(initial)
                pendingShow =
                  scope.launch {
                    delay(CHIP_SHOW_DELAY_MS)
                    chipShown = true
                    armedIdx = current
                  }

                while (true) {
                  val event = awaitPointerEvent()
                  val change: PointerInputChange =
                    event.changes.firstOrNull { it.id == down.id } ?: continue
                  if (change.changedToUp()) {
                    val final = current
                    if (final != null && specsRef[final].enabled) {
                      specsRef[final].onInvoke()
                    }
                    endPress(current, cancel = false)
                    current = null
                    change.consume()
                    return@awaitEachGesture
                  }
                  if (change.isConsumed) {
                    return@awaitEachGesture
                  }
                  val next = hitTest(change.position)
                  if (next != current) {
                    endPress(current, cancel = false)
                    current = next
                    startPress(next)
                    if (chipShown) armedIdx = next
                  }
                }
              } finally {
                pendingShow?.cancel()
                endPress(current, cancel = true)
                armedIdx = null
                chipShown = false
              }
            }
          },
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        for (i in 0 until TOP_ROW_SIZE) {
          BarButton(spec = specs[i], index = i, bounds = bounds)
        }
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        for (i in 0 until BOTTOM_ROW_SIZE) {
          val idx = TOP_ROW_SIZE + i
          BarButton(spec = specs[idx], index = idx, bounds = bounds)
        }
      }
    }

    PreviewChip(
      armedIdx = armedIdx,
      specs = specs,
      bounds = bounds,
      modifier = Modifier.align(Alignment.TopStart),
    )
  }
}
