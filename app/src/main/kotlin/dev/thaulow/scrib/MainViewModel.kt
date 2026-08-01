package dev.thaulow.scrib

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.thaulow.scrib.data.NoteRepository
import dev.thaulow.scrib.data.Snapshot
import dev.thaulow.scrib.data.UndoStackDto
import dev.thaulow.scrib.data.UndoStackRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

private const val COALESCE_WINDOW_MS = 2000L
private const val MAX_HISTORY = 100

class MainViewModel(
  private val noteRepo: NoteRepository,
  private val undoRepo: UndoStackRepository,
  private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
  var value by mutableStateOf(TextFieldValue("", TextRange.Zero))
    private set

  val canUndo: Boolean get() = index > 0
  val canRedo: Boolean get() = index < entries.lastIndex

  private var entries: MutableList<Snapshot> =
    mutableListOf(Snapshot("", 0, 0))
  private var index: Int = 0
  private var lastCoalesceAt: Long = 0L
  private var hasLocalChanges = false
  private var hasPendingPersistence = false
  private val _saveError = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  val saveError: SharedFlow<Unit> = _saveError.asSharedFlow()

  val pendingShareFlow: StateFlow<String?> =
    savedStateHandle.getStateFlow(PENDING_SHARE_KEY, null)
  var pendingShare: String?
    get() = pendingShareFlow.value
    private set(value) {
      savedStateHandle[PENDING_SHARE_KEY] = value
    }

  val pendingTextSourceFlow: StateFlow<TextSource?> =
    savedStateHandle
      .getStateFlow<String?>(PENDING_TEXT_SOURCE_KEY, null)
      .map { name -> enumValues<TextSource>().firstOrNull { it.name == name } }
      .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  init {
    viewModelScope.launch {
      val loaded = withContext(Dispatchers.IO) { loadInitialState() }
      if (hasLocalChanges || loaded == null) return@launch
      entries = loaded.entries
      index = loaded.index
      value = loaded.current.toTextFieldValue()
    }
  }

  private fun loadInitialState(): InitialState? {
    val dto = undoRepo.load()
    if (dto != null && dto.entries.isNotEmpty()) {
      val loadedEntries = dto.entries.toMutableList()
      val loadedIndex = dto.index.coerceIn(0, loadedEntries.lastIndex)
      return InitialState(loadedEntries, loadedIndex, loadedEntries[loadedIndex])
    }
    val text = noteRepo.load()
    val seed = Snapshot(text, 0, 0)
    return InitialState(mutableListOf(seed), 0, seed)
  }

  fun onValueChange(new: TextFieldValue) {
    hasLocalChanges = true
    val textChanged = new.text != value.text
    value = new
    if (!textChanged) {
      // cursor-only move: update current snapshot's cursor without a new history entry
      entries[index] = Snapshot(new.text, new.selection.start, new.selection.end)
      hasPendingPersistence = true
      return
    }
    val now = System.currentTimeMillis()
    val coalesce = now - lastCoalesceAt < COALESCE_WINDOW_MS && index == entries.lastIndex
    if (coalesce) {
      entries[index] = Snapshot(new.text, new.selection.start, new.selection.end)
    } else {
      pushSnapshotFromValue()
    }
    lastCoalesceAt = now
    hasPendingPersistence = true
  }

  fun handleSharedText(
    text: String,
    key: String,
    forceHandle: Boolean = false,
    source: TextSource = TextSource.SHARE,
  ) {
    if (!forceHandle && isShareHandled(key)) return
    pendingShare = text
    savedStateHandle[PENDING_TEXT_SOURCE_KEY] = source.name
    savedStateHandle[LAST_HANDLED_SHARE_KEY] = key
  }

  fun markShareHandled(
    key: String,
    forceHandle: Boolean = false,
  ) {
    if (!forceHandle && isShareHandled(key)) return
    savedStateHandle[LAST_HANDLED_SHARE_KEY] = key
  }

  fun clearPendingShare() {
    pendingShare = null
    savedStateHandle[PENDING_TEXT_SOURCE_KEY] = null
  }

  fun clear() {
    if (value.text.isEmpty()) return
    setValueAndPush(TextFieldValue("", TextRange.Zero))
  }

  fun replaceWith(pasted: String) {
    val normalized = normalize(pasted)
    if (normalized == value.text) return
    setValueAndPush(TextFieldValue(normalized, TextRange(normalized.length)))
  }

  fun append(pasted: String) {
    val normalized = normalize(pasted)
    if (normalized.isEmpty()) return
    val combined = if (value.text.isEmpty()) normalized else "${value.text}\n\n$normalized"
    setValueAndPush(TextFieldValue(combined, TextRange(combined.length)))
  }

  fun undo() {
    if (!canUndo) return
    index -= 1
    value = entries[index].toTextFieldValue()
    lastCoalesceAt = 0L
    flushPendingSave()
  }

  fun redo() {
    if (!canRedo) return
    index += 1
    value = entries[index].toTextFieldValue()
    lastCoalesceAt = 0L
    flushPendingSave()
  }

  fun moveCursor(delta: Int) {
    val len = value.text.length
    val sel = value.selection
    val newPos =
      when {
        !sel.collapsed && delta < 0 -> sel.min
        !sel.collapsed && delta > 0 -> sel.max
        else -> (sel.end + delta).coerceIn(0, len)
      }
    onValueChange(value.copy(selection = TextRange(newPos)))
  }

  fun moveCursorToLine(
    delta: Int,
    layout: TextLayoutResult,
  ) {
    val cursor = value.selection.end
    val currentLine = layout.getLineForOffset(cursor)
    val targetLine = currentLine + delta
    if (targetLine < 0 || targetLine >= layout.lineCount) return
    val x = layout.getHorizontalPosition(cursor, true)
    val y = (layout.getLineTop(targetLine) + layout.getLineBottom(targetLine)) / 2f
    val newPos = layout.getOffsetForPosition(Offset(x, y))
    onValueChange(value.copy(selection = TextRange(newPos)))
  }

  fun moveLine(delta: Int) {
    val moved = moveLines(value.text, value.selection, delta) ?: return
    setValueAndPush(moved)
  }

  fun selectCurrentWord() {
    val text = value.text
    if (text.isEmpty()) return
    val caret = value.selection.end
    val isWord: (Char) -> Boolean = { it.isLetterOrDigit() || it == '_' }
    val pivot =
      when {
        caret < text.length && isWord(text[caret]) -> caret
        caret > 0 && isWord(text[caret - 1]) -> caret - 1
        else -> return
      }
    var start = pivot
    while (start > 0 && isWord(text[start - 1])) start--
    var end = pivot + 1
    while (end < text.length && isWord(text[end])) end++
    onValueChange(value.copy(selection = TextRange(start, end)))
  }

  fun flushPendingSave() {
    if (!hasPendingPersistence) return
    val success = runBlocking(Dispatchers.IO) { persist() }
    hasPendingPersistence = false
    if (!success) _saveError.tryEmit(Unit)
  }

  private fun setValueAndPush(new: TextFieldValue) {
    value = new
    pushSnapshotFromValue()
    lastCoalesceAt = 0L
    hasPendingPersistence = true
  }

  private fun pushSnapshotFromValue() {
    // truncate redo tail
    if (index < entries.lastIndex) {
      while (entries.lastIndex > index) entries.removeAt(entries.lastIndex)
    }
    entries.add(Snapshot(value.text, value.selection.start, value.selection.end))
    // cap
    while (entries.size > MAX_HISTORY) {
      entries.removeAt(0)
    }
    index = entries.lastIndex
  }

  private fun persist(): Boolean {
    val noteOk = runCatching { noteRepo.save(value.text) }.isSuccess
    val undoOk = runCatching { undoRepo.save(UndoStackDto(entries.toList(), index)) }.isSuccess
    return noteOk && undoOk
  }

  private fun isShareHandled(key: String): Boolean = savedStateHandle.get<String>(LAST_HANDLED_SHARE_KEY) == key

  private data class InitialState(
    val entries: MutableList<Snapshot>,
    val index: Int,
    val current: Snapshot,
  )

  private companion object {
    const val PENDING_SHARE_KEY = "pending_share"
    const val PENDING_TEXT_SOURCE_KEY = "pending_text_source"
    const val LAST_HANDLED_SHARE_KEY = "last_handled_share"
  }

  private fun normalize(s: String): String = if ('\r' in s) s.replace("\r\n", "\n").replace('\r', '\n') else s

  private fun Snapshot.toTextFieldValue(): TextFieldValue {
    val len = text.length
    val start = cursorStart.coerceIn(0, len)
    val end = cursorEnd.coerceIn(0, len)
    return TextFieldValue(text, TextRange(start, end))
  }
}

/**
 * Swaps the logical line(s) covered by [selection] with the neighbouring line, [delta] being -1
 * for up and +1 for down. Returns null when there is no such neighbour, i.e. the block already
 * sits at that end of the text.
 *
 * Deliberately works on logical `\n` lines rather than the visual, wrap-aware lines that
 * [MainViewModel.moveCursorToLine] steps through: half of a wrapped paragraph is not something
 * you can reorder. The selection travels with the text so the action can be repeated to carry a
 * line several positions.
 */
internal fun moveLines(
  text: String,
  selection: TextRange,
  delta: Int,
): TextFieldValue? {
  if (delta != -1 && delta != 1) return null

  val blockStart = text.lastIndexOf('\n', selection.min - 1) + 1
  val newlineAfter = text.indexOf('\n', selection.max)
  val blockEnd = if (newlineAfter == -1) text.length else newlineAfter
  val block = text.substring(blockStart, blockEnd)

  val moved: String
  val shift: Int
  if (delta < 0) {
    if (blockStart == 0) return null
    // -2 skips the newline that terminates the previous line; a negative index yields -1, which
    // correctly puts prevStart at 0 when the previous line is the first one.
    val prevStart = text.lastIndexOf('\n', blockStart - 2) + 1
    val prevLine = text.substring(prevStart, blockStart - 1)
    moved = text.substring(0, prevStart) + block + "\n" + prevLine + text.substring(blockEnd)
    shift = -(blockStart - prevStart)
  } else {
    if (blockEnd == text.length) return null
    val newlineAfterNext = text.indexOf('\n', blockEnd + 1)
    val nextEnd = if (newlineAfterNext == -1) text.length else newlineAfterNext
    val nextLine = text.substring(blockEnd + 1, nextEnd)
    moved = text.substring(0, blockStart) + nextLine + "\n" + block + text.substring(nextEnd)
    shift = nextEnd - blockEnd
  }

  // Reordering only permutes the text, so the shifted selection always stays in range.
  val start = (selection.start + shift).coerceIn(0, moved.length)
  val end = (selection.end + shift).coerceIn(0, moved.length)
  return TextFieldValue(moved, TextRange(start, end))
}
