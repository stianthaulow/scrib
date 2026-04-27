package dev.thaulow.scrib.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UndoStackRepositoryTest {
  @get:Rule
  val tmp = TemporaryFolder()

  @Test
  fun `load returns null when file does not exist`() {
    assertNull(UndoStackRepository(File(tmp.root, "undo.json")).load())
  }

  @Test
  fun `save and load roundtrip preserves all fields`() {
    val repo = UndoStackRepository(File(tmp.root, "undo.json"))
    val dto =
      UndoStackDto(
        entries =
          listOf(
            Snapshot("hello", 3, 3),
            Snapshot("hello world", 11, 11),
          ),
        index = 1,
      )
    repo.save(dto)
    assertEquals(dto, repo.load())
  }

  @Test
  fun `load returns null for corrupted json`() {
    val file = tmp.newFile("undo.json").also { it.writeText("not valid json {{{") }
    assertNull(UndoStackRepository(file).load())
  }

  @Test
  fun `load ignores unknown keys`() {
    val file =
      tmp.newFile("undo.json").also {
        it.writeText("""{"entries":[{"text":"hi","cursorStart":2,"cursorEnd":2,"future":"x"}],"index":0}""")
      }
    assertEquals(
      "hi",
      UndoStackRepository(file)
        .load()
        ?.entries
        ?.first()
        ?.text,
    )
  }
}
