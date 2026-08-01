package dev.thaulow.scrib.ui

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private val BUTTON = Rect(left = 100f, top = 200f, right = 148f, bottom = 248f)
private const val THRESHOLD = 24f

class IsPromotedUpTest {
  @Test
  fun `not promoted while the finger is still on the button`() {
    assertFalse(isPromoted(BUTTON, PromoteDirection.UP, posY = 220f, thresholdPx = THRESHOLD))
  }

  @Test
  fun `not promoted just above the button but inside the threshold`() {
    assertFalse(isPromoted(BUTTON, PromoteDirection.UP, posY = 190f, thresholdPx = THRESHOLD))
  }

  @Test
  fun `not promoted exactly at the threshold`() {
    assertFalse(isPromoted(BUTTON, PromoteDirection.UP, posY = 176f, thresholdPx = THRESHOLD))
  }

  @Test
  fun `promoted once past the threshold`() {
    assertTrue(isPromoted(BUTTON, PromoteDirection.UP, posY = 175f, thresholdPx = THRESHOLD))
  }

  @Test
  fun `promoted far above the bar`() {
    assertTrue(isPromoted(BUTTON, PromoteDirection.UP, posY = 0f, thresholdPx = THRESHOLD))
  }

  @Test
  fun `swiping the wrong way never promotes`() {
    assertFalse(isPromoted(BUTTON, PromoteDirection.UP, posY = 400f, thresholdPx = THRESHOLD))
  }
}

class IsPromotedDownTest {
  @Test
  fun `not promoted while the finger is still on the button`() {
    assertFalse(isPromoted(BUTTON, PromoteDirection.DOWN, posY = 220f, thresholdPx = THRESHOLD))
  }

  @Test
  fun `not promoted just below the button but inside the threshold`() {
    assertFalse(isPromoted(BUTTON, PromoteDirection.DOWN, posY = 260f, thresholdPx = THRESHOLD))
  }

  @Test
  fun `not promoted exactly at the threshold`() {
    assertFalse(isPromoted(BUTTON, PromoteDirection.DOWN, posY = 272f, thresholdPx = THRESHOLD))
  }

  @Test
  fun `promoted once past the threshold`() {
    assertTrue(isPromoted(BUTTON, PromoteDirection.DOWN, posY = 273f, thresholdPx = THRESHOLD))
  }

  @Test
  fun `swiping the wrong way never promotes`() {
    assertFalse(isPromoted(BUTTON, PromoteDirection.DOWN, posY = 0f, thresholdPx = THRESHOLD))
  }
}

class IsPromotedDegenerateBoundsTest {
  @Test
  fun `an unmeasured button never promotes`() {
    // Bounds start at Rect.Zero before the first layout pass.
    assertFalse(isPromoted(Rect.Zero, PromoteDirection.UP, posY = -100f, thresholdPx = THRESHOLD))
    assertFalse(isPromoted(Rect.Zero, PromoteDirection.DOWN, posY = 100f, thresholdPx = THRESHOLD))
  }
}
