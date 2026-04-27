package dev.thaulow.scrib.data

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NoteRepositoryTest {
  @get:Rule
  val tmp = TemporaryFolder()

  @Test
  fun `load returns empty string when file does not exist`() {
    val repo = NoteRepository(File(tmp.root, "note.txt"))
    assertEquals("", repo.load())
  }

  @Test
  fun `load returns file content`() {
    val file = tmp.newFile("note.txt").also { it.writeText("hello") }
    assertEquals("hello", NoteRepository(file).load())
  }

  @Test
  fun `save and load roundtrip`() {
    val repo = NoteRepository(File(tmp.root, "note.txt"))
    repo.save("roundtrip content")
    assertEquals("roundtrip content", repo.load())
  }

  @Test
  fun `save overwrites existing content`() {
    val repo = NoteRepository(File(tmp.root, "note.txt"))
    repo.save("first")
    repo.save("second")
    assertEquals("second", repo.load())
  }
}
