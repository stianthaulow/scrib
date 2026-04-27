package dev.thaulow.scrib.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun ShareDialog(
  onReplace: () -> Unit,
  onAppend: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Shared text") },
    text = { Text("Replace the note with this text, or append it to the end?") },
    confirmButton = {
      TextButton(onClick = onAppend) { Text("Append") }
    },
    dismissButton = {
      TextButton(onClick = onReplace) { Text("Replace") }
    },
  )
}
