package dev.thaulow.scrib.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AtomicFileTest {
  @get:Rule
  val tmp = TemporaryFolder()

  @Test
  fun `writeAtomically creates file with correct content`() {
    val file = File(tmp.root, "out.txt")
    file.writeAtomically("content")
    assertEquals("content", file.readText())
  }

  @Test
  fun `writeAtomically overwrites existing file`() {
    val file = tmp.newFile("out.txt").also { it.writeText("old") }
    file.writeAtomically("new")
    assertEquals("new", file.readText())
  }

  @Test
  fun `writeAtomically leaves no temp file behind`() {
    val file = File(tmp.root, "out.txt")
    file.writeAtomically("content")
    assertFalse(File(tmp.root, "out.txt.tmp").exists())
  }

  @Test
  fun `writeAtomically creates missing parent directories`() {
    val file = File(tmp.root, "nested/dir/out.txt")
    file.writeAtomically("nested")
    assertTrue(file.exists())
    assertEquals("nested", file.readText())
  }
}
