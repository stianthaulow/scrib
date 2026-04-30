package dev.thaulow.scrib

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.thaulow.scrib.data.NoteRepository
import dev.thaulow.scrib.data.UndoStackRepository
import dev.thaulow.scrib.ui.BottomBar
import dev.thaulow.scrib.ui.ScribTheme
import dev.thaulow.scrib.ui.ShareDialog
import java.io.File

class MainActivity : ComponentActivity() {
  companion object {
    const val EXTRA_TILE_LAUNCH = "tile_launch"
  }

  private val viewModel: MainViewModel by viewModels {
    viewModelFactory {
      initializer {
        MainViewModel(
          NoteRepository(File(filesDir, "scrib.txt")),
          UndoStackRepository(File(filesDir, "scrib.undo.json")),
          createSavedStateHandle(),
        )
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    captureShareIntent(intent)
    captureTileLaunch(intent)

    lifecycle.addObserver(
      LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_PAUSE) {
          viewModel.flushPendingSave()
        }
      },
    )

    setContent {
      ScribTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background,
        ) {
          EditorScreen(
            viewModel = viewModel,
          )
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    captureShareIntent(intent, dedup = false)
    captureTileLaunch(intent, dedup = false)
  }

  private fun captureTileLaunch(
    intent: Intent?,
    dedup: Boolean = true,
  ) {
    if (intent?.getBooleanExtra(EXTRA_TILE_LAUNCH, false) != true) return
    intent.removeExtra(EXTRA_TILE_LAUNCH)
    val text = readClipboardAsText(this) ?: return
    if (text.isBlank()) return
    val cleaned = cleanSharedText(text)
    if (cleaned.isEmpty()) return
    viewModel.handleSharedText(cleaned, key = "tile|${text.hashCode()}", dedup = dedup, source = TextSource.TILE)
  }

  private fun captureShareIntent(
    intent: Intent?,
    dedup: Boolean = true,
  ) {
    if (intent == null || intent.action != Intent.ACTION_SEND) return
    val raw = intent.getStringExtra(Intent.EXTRA_TEXT)
    val key = buildShareKey(intent, raw)
    if (intent.type != "text/plain") {
      viewModel.markShareHandled(key, dedup = dedup)
      return
    }
    if (raw == null) {
      viewModel.markShareHandled(key, dedup = dedup)
      return
    }
    val text = cleanSharedText(raw)
    if (text.isEmpty()) {
      viewModel.markShareHandled(key, dedup = dedup)
      return
    }
    viewModel.handleSharedText(text, key, dedup = dedup)
  }

  private fun buildShareKey(
    intent: Intent,
    raw: String?,
  ): String = "${intent.action}|${intent.type}|${intent.`package`}|${raw.orEmpty()}".hashCode().toString()
}

private val TRAILING_URL = Regex("\\s*https?://\\S+\\s*$")
private val QUOTE_PAIRS = listOf('"' to '"', '\u201C' to '\u201D', '\u00AB' to '\u00BB')

private fun cleanSharedText(text: String): String = stripSurroundingQuotes(stripTrailingUrl(text)).trim()

private fun stripTrailingUrl(text: String): String {
  val trimmed = text.trimEnd()
  val match = TRAILING_URL.find(trimmed) ?: return trimmed
  val before = trimmed.substring(0, match.range.first).trimEnd()
  return if (before.isEmpty()) trimmed else before
}

private fun stripSurroundingQuotes(text: String): String {
  val trimmed = text.trim()
  if (trimmed.length < 2) return trimmed
  for ((open, close) in QUOTE_PAIRS) {
    if (trimmed.first() == open && trimmed.last() == close) {
      return trimmed.substring(1, trimmed.length - 1)
    }
  }
  return trimmed
}

@Composable
private fun EditorScreen(viewModel: MainViewModel) {
  val context = LocalContext.current
  val focusRequester = remember { FocusRequester() }
  val keyboard = LocalSoftwareKeyboardController.current
  val density = LocalDensity.current
  val imeVisible = WindowInsets.ime.getBottom(density) > 0
  val pendingShare = viewModel.pendingShareFlow.collectAsStateWithLifecycle().value
  val pendingTextSource = viewModel.pendingTextSourceFlow.collectAsStateWithLifecycle().value
  var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
    keyboard?.show()
  }

  LaunchedEffect(viewModel) {
    viewModel.saveError.collect {
      Toast.makeText(context, "Failed to save — storage may be full", Toast.LENGTH_LONG).show()
    }
  }

  val onToggleKeyboard: () -> Unit = {
    if (imeVisible) {
      keyboard?.hide()
    } else {
      focusRequester.requestFocus()
      keyboard?.show()
    }
  }

  val onCopy: () -> Unit = {
    val v = viewModel.value
    val text =
      if (v.selection.collapsed) {
        v.text
      } else {
        v.text.substring(v.selection.min, v.selection.max)
      }
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("Scrib", text))
  }

  val onReplace: () -> Unit = {
    val pasted = readClipboardAsText(context)
    if (pasted.isNullOrEmpty()) {
      Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
    } else {
      viewModel.replaceWith(pasted)
    }
  }

  val onAppend: () -> Unit = {
    val pasted = readClipboardAsText(context)
    if (pasted.isNullOrEmpty()) {
      Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
    } else {
      viewModel.append(pasted)
    }
  }

  Column(
    modifier =
      Modifier
        .fillMaxSize()
        .statusBarsPadding(),
  ) {
    BasicTextField(
      value = viewModel.value,
      onValueChange = viewModel::onValueChange,
      modifier =
        Modifier
          .weight(1f)
          .fillMaxWidth()
          .padding(16.dp)
          .focusRequester(focusRequester),
      textStyle =
        TextStyle(
          fontSize = 16.sp,
          color = MaterialTheme.colorScheme.onBackground,
        ),
      cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
      onTextLayout = { textLayout = it },
    )
    BottomBar(
      imeVisible = imeVisible,
      canUndo = viewModel.canUndo,
      canRedo = viewModel.canRedo,
      onToggleKeyboard = onToggleKeyboard,
      onClear = viewModel::clear,
      onCopy = onCopy,
      onReplace = onReplace,
      onAppend = onAppend,
      onUndo = viewModel::undo,
      onRedo = viewModel::redo,
      onMoveLeft = { viewModel.moveCursor(-1) },
      onMoveRight = { viewModel.moveCursor(1) },
      onMoveUp = { textLayout?.let { viewModel.moveCursorToLine(-1, it) } },
      onMoveDown = { textLayout?.let { viewModel.moveCursorToLine(1, it) } },
      onSelectWord = viewModel::selectCurrentWord,
      modifier =
        Modifier
          .navigationBarsPadding()
          .imePadding(),
    )
  }

  if (pendingShare != null) {
    val (dialogTitle, dialogBody) =
      when (pendingTextSource) {
        TextSource.TILE ->
          "Clipboard text" to
            "You have text on the clipboard. Replace the note with it, or append it to the end?"
        TextSource.SHARE ->
          "Shared text" to
            "Replace the note with this text, or append it to the end?"
      }
    ShareDialog(
      title = dialogTitle,
      body = dialogBody,
      onReplace = {
        viewModel.replaceWith(pendingShare)
        viewModel.clearPendingShare()
      },
      onAppend = {
        viewModel.append(pendingShare)
        viewModel.clearPendingShare()
      },
      onDismiss = viewModel::clearPendingShare,
    )
  }
}

private fun readClipboardAsText(context: Context): String? {
  val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  val clip = manager.primaryClip ?: return null
  if (clip.itemCount == 0) return null
  return clip.getItemAt(0).coerceToText(context)?.toString()
}
