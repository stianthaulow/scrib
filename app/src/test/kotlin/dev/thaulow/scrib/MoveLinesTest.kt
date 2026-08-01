package dev.thaulow.scrib

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Cursor at [at], no selection. */
private fun caret(at: Int) = TextRange(at)

class MoveLinesUpTest {
  @Test
  fun `middle line swaps with the one above`() {
    val moved = moveLines("a\nb\nc", caret(2), -1)
    assertEquals("b\na\nc", moved?.text)
  }

  @Test
  fun `last line swaps with the one above`() {
    val moved = moveLines("a\nb\nc", caret(4), -1)
    assertEquals("a\nc\nb", moved?.text)
  }

  @Test
  fun `first line has nowhere to go`() {
    assertNull(moveLines("a\nb\nc", caret(0), -1))
  }

  @Test
  fun `caret rides along with the line`() {
    // Caret sits on "b"; after the swap "b" starts at 0, so the caret lands there too.
    val moved = moveLines("a\nb\nc", caret(2), -1)
    assertEquals(TextRange(0), moved?.selection)
  }

  @Test
  fun `caret keeps its offset within the line`() {
    // Caret one char into "bb" (offset 3); "bb" moves to 0, so the caret lands at 1.
    val moved = moveLines("a\nbb\nc", caret(3), -1)
    assertEquals("bb\na\nc", moved?.text)
    assertEquals(TextRange(1), moved?.selection)
  }

  @Test
  fun `repeating the move carries a line further`() {
    val once = moveLines("a\nb\nc", caret(4), -1)!!
    val twice = moveLines(once.text, once.selection, -1)!!
    assertEquals("c\na\nb", twice.text)
    // Still on "c", so a third call would be a no-op rather than moving a different line.
    assertEquals(TextRange(0), twice.selection)
    assertNull(moveLines(twice.text, twice.selection, -1))
  }
}

class MoveLinesDownTest {
  @Test
  fun `middle line swaps with the one below`() {
    val moved = moveLines("a\nb\nc", caret(2), 1)
    assertEquals("a\nc\nb", moved?.text)
  }

  @Test
  fun `first line swaps with the one below`() {
    val moved = moveLines("a\nb\nc", caret(0), 1)
    assertEquals("b\na\nc", moved?.text)
  }

  @Test
  fun `last line has nowhere to go`() {
    assertNull(moveLines("a\nb\nc", caret(4), 1))
  }

  @Test
  fun `caret rides along with the line`() {
    val moved = moveLines("a\nb\nc", caret(2), 1)
    assertEquals(TextRange(4), moved?.selection)
  }

  @Test
  fun `moves past a trailing empty line`() {
    // "a\nb\n" is three logical lines: "a", "b", "".
    val moved = moveLines("a\nb\n", caret(2), 1)
    assertEquals("a\n\nb", moved?.text)
  }
}

class MoveLinesSelectionTest {
  @Test
  fun `a selection spanning two lines moves as one block`() {
    // Selection covers "b\nc".
    val moved = moveLines("a\nb\nc\nd", TextRange(2, 5), 1)
    assertEquals("a\nd\nb\nc", moved?.text)
  }

  @Test
  fun `the block selection is preserved so the move can be repeated`() {
    val moved = moveLines("a\nb\nc\nd", TextRange(2, 5), 1)
    assertEquals(TextRange(4, 7), moved?.selection)
  }

  @Test
  fun `a block at the bottom has nowhere to go`() {
    assertNull(moveLines("a\nb\nc", TextRange(2, 5), 1))
  }

  @Test
  fun `selection direction survives the move`() {
    // Reversed selection: start after end.
    val moved = moveLines("a\nb\nc", TextRange(3, 2), 1)
    assertEquals(TextRange(5, 4), moved?.selection)
  }

  @Test
  fun `a caret resting on the newline belongs to the line it terminates`() {
    // Offset 1 is the "\n" after "a", which is the end of line "a", not the start of "b".
    val moved = moveLines("a\nb\nc", caret(1), 1)
    assertEquals("b\na\nc", moved?.text)
  }
}

class MoveLinesEdgeCaseTest {
  @Test
  fun `empty text cannot move in either direction`() {
    assertNull(moveLines("", caret(0), -1))
    assertNull(moveLines("", caret(0), 1))
  }

  @Test
  fun `single line cannot move in either direction`() {
    assertNull(moveLines("only", caret(2), -1))
    assertNull(moveLines("only", caret(2), 1))
  }

  @Test
  fun `blank lines are ordinary lines`() {
    assertEquals("\na\nb", moveLines("a\n\nb", caret(0), 1)?.text)
  }

  @Test
  fun `a leading empty line can be swapped down`() {
    assertEquals("a\n\nb", moveLines("\na\nb", caret(0), 1)?.text)
  }

  @Test
  fun `deltas other than one line are rejected`() {
    assertNull(moveLines("a\nb\nc", caret(2), 0))
    assertNull(moveLines("a\nb\nc", caret(2), 2))
    assertNull(moveLines("a\nb\nc", caret(2), -2))
  }

  @Test
  fun `moving never changes the character count`() {
    val text = "one\ntwo\nthree"
    assertEquals(text.length, moveLines(text, caret(4), -1)!!.text.length)
    assertEquals(text.length, moveLines(text, caret(4), 1)!!.text.length)
  }

  @Test
  fun `up then down returns the original text`() {
    val text = "one\ntwo\nthree"
    val up = moveLines(text, caret(4), -1)!!
    val back = moveLines(up.text, up.selection, 1)!!
    assertEquals(text, back.text)
    assertEquals(caret(4), back.selection)
  }
}
