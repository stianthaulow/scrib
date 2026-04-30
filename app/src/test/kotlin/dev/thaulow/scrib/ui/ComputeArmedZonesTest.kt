package dev.thaulow.scrib.ui

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComputeArmedZonesTest {
  @Test
  fun `empty bounds returns null`() {
    assertNull(computeArmedZones(emptyList(), emptyList(), 100f))
  }

  @Test
  fun `bounds and rowIndex size mismatch returns null`() {
    assertNull(computeArmedZones(listOf(Rect(0f, 0f, 10f, 10f)), listOf(0, 1), 100f))
  }

  @Test
  fun `zero-width rect returns null`() {
    assertNull(computeArmedZones(listOf(Rect(0f, 0f, 0f, 10f)), listOf(0), 100f))
  }

  @Test
  fun `zero-height rect returns null`() {
    assertNull(computeArmedZones(listOf(Rect(0f, 0f, 10f, 0f)), listOf(0), 100f))
  }

  @Test
  fun `zero barWidthPx returns null`() {
    assertNull(computeArmedZones(listOf(Rect(0f, 0f, 10f, 10f)), listOf(0), 0f))
  }

  @Test
  fun `single button spans full bar width`() {
    val zones = computeArmedZones(listOf(Rect(40f, 0f, 60f, 20f)), listOf(0), 100f)!!
    assertEquals(1, zones.size)
    assertEquals(Rect(0f, 0f, 100f, 20f), zones[0])
  }

  @Test
  fun `two buttons split at midpoint`() {
    val a = Rect(10f, 0f, 30f, 20f) // center 20
    val b = Rect(60f, 0f, 80f, 20f) // center 70
    val zones = computeArmedZones(listOf(a, b), listOf(0, 0), 100f)!!
    assertEquals(Rect(0f, 0f, 45f, 20f), zones[0])
    assertEquals(Rect(45f, 0f, 100f, 20f), zones[1])
  }

  @Test
  fun `three buttons split at neighbor midpoints`() {
    val a = Rect(10f, 0f, 30f, 20f) // center 20
    val b = Rect(40f, 0f, 60f, 20f) // center 50
    val c = Rect(70f, 0f, 90f, 20f) // center 80
    val zones = computeArmedZones(listOf(a, b, c), listOf(0, 0, 0), 100f)!!
    assertEquals(Rect(0f, 0f, 35f, 20f), zones[0])
    assertEquals(Rect(35f, 0f, 65f, 20f), zones[1])
    assertEquals(Rect(65f, 0f, 100f, 20f), zones[2])
  }

  @Test
  fun `two rows are independent`() {
    val topA = Rect(10f, 0f, 30f, 20f) // center 20
    val topB = Rect(70f, 0f, 90f, 20f) // center 80
    val botA = Rect(20f, 30f, 40f, 50f) // center 30
    val botB = Rect(60f, 30f, 80f, 50f) // center 70
    val zones =
      computeArmedZones(
        listOf(topA, topB, botA, botB),
        listOf(0, 0, 1, 1),
        100f,
      )!!
    assertEquals(Rect(0f, 0f, 50f, 20f), zones[0])
    assertEquals(Rect(50f, 0f, 100f, 20f), zones[1])
    assertEquals(Rect(0f, 30f, 50f, 50f), zones[2])
    assertEquals(Rect(50f, 30f, 100f, 50f), zones[3])
  }

  @Test
  fun `irregular index order is sorted by center x`() {
    val a = Rect(70f, 0f, 90f, 20f) // idx 0, center 80
    val b = Rect(40f, 0f, 60f, 20f) // idx 1, center 50
    val c = Rect(10f, 0f, 30f, 20f) // idx 2, center 20
    val zones = computeArmedZones(listOf(a, b, c), listOf(0, 0, 0), 100f)!!
    assertEquals(Rect(65f, 0f, 100f, 20f), zones[0])
    assertEquals(Rect(35f, 0f, 65f, 20f), zones[1])
    assertEquals(Rect(0f, 0f, 35f, 20f), zones[2])
  }
}
