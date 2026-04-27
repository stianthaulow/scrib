package dev.thaulow.scrib.data

import java.io.File

class NoteRepository(
  private val noteFile: File,
) {
  fun load(): String = if (noteFile.exists()) noteFile.readText(Charsets.UTF_8) else ""

  fun save(text: String) {
    noteFile.writeAtomically(text)
  }
}
