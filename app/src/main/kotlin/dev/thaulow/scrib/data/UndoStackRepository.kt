package dev.thaulow.scrib.data

import kotlinx.serialization.json.Json
import java.io.File

class UndoStackRepository(
  private val file: File,
) {
  private val json = Json { ignoreUnknownKeys = true }

  fun load(): UndoStackDto? {
    if (!file.exists()) return null
    return runCatching { json.decodeFromString<UndoStackDto>(file.readText(Charsets.UTF_8)) }
      .getOrNull()
  }

  fun save(dto: UndoStackDto) {
    file.writeAtomically(json.encodeToString(UndoStackDto.serializer(), dto))
  }
}
