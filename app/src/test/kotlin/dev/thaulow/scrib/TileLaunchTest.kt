package dev.thaulow.scrib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TileClipboardTextTest {
  @Test
  fun `null clip returns null`() {
    assertNull(tileClipboardText(null))
  }

  @Test
  fun `empty clip returns null`() {
    assertNull(tileClipboardText(""))
  }

  @Test
  fun `whitespace-only clip returns null`() {
    assertNull(tileClipboardText("   "))
    assertNull(tileClipboardText("\n\t  \n"))
  }

  @Test
  fun `clip that cleans down to nothing returns null`() {
    // A bare pair of quotes strips to the empty string.
    assertNull(tileClipboardText("\"\""))
  }

  @Test
  fun `plain text passes through`() {
    assertEquals("hello world", tileClipboardText("hello world"))
  }

  @Test
  fun `surrounding whitespace is trimmed`() {
    assertEquals("hello", tileClipboardText("  \n hello \t "))
  }

  @Test
  fun `trailing url is stripped`() {
    assertEquals("read this", tileClipboardText("read this https://example.com/a/b"))
  }

  @Test
  fun `bare url is kept rather than stripped to nothing`() {
    assertEquals("https://example.com", tileClipboardText("https://example.com"))
  }

  @Test
  fun `straight quotes are unwrapped`() {
    assertEquals("hello", tileClipboardText("\"hello\""))
  }

  @Test
  fun `curly quotes are unwrapped`() {
    assertEquals("hello", tileClipboardText("“hello”"))
  }

  @Test
  fun `guillemets are unwrapped`() {
    assertEquals("hello", tileClipboardText("«hello»"))
  }

  @Test
  fun `quotes and trailing url are both removed`() {
    assertEquals("a quote", tileClipboardText("\"a quote\" https://example.com"))
  }

  @Test
  fun `interior url is left alone`() {
    assertEquals("see https://example.com for more", tileClipboardText("see https://example.com for more"))
  }

  @Test
  fun `multiline text is preserved`() {
    assertEquals("line one\nline two", tileClipboardText("line one\nline two"))
  }
}

class TileLaunchKeyTest {
  @Test
  fun `key is prefixed to avoid colliding with share keys`() {
    assertTrue(tileLaunchKey("hello").startsWith("tile|"))
  }

  @Test
  fun `same clip yields the same key`() {
    assertEquals(tileLaunchKey("hello"), tileLaunchKey("hello"))
  }

  @Test
  fun `different clips yield different keys`() {
    assertNotEquals(tileLaunchKey("hello"), tileLaunchKey("goodbye"))
  }

  @Test
  fun `key is derived from the raw clip, not the cleaned text`() {
    // Both clean to "hello", but they are distinct clips and must not dedup against each other.
    assertEquals("hello", tileClipboardText("\"hello\""))
    assertEquals("hello", tileClipboardText("hello"))
    assertNotEquals(tileLaunchKey("\"hello\""), tileLaunchKey("hello"))
  }
}
