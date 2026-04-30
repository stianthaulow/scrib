package dev.thaulow.scrib.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

internal val CHIP_VERTICAL_GAP = 8.dp
internal const val CHIP_FADE_MS = 100
internal val CHIP_SLIDE_OFFSET = 4.dp
internal const val CHIP_MOVE_MS = 120
internal val CHIP_MAX_WIDTH = 200.dp

internal data class BarButtonSpec(
  val label: String,
  val contentDescription: String,
  val enabled: Boolean,
  val onInvoke: () -> Unit,
  val icon: ImageVector,
  val interactionSource: MutableInteractionSource,
)

@Stable
internal class BarBoundsState(
  rowIndex: List<Int>,
) {
  val rowIndex: List<Int> = rowIndex.toList()
  val tightBounds = mutableStateListOf<Rect>().apply { repeat(rowIndex.size) { add(Rect.Zero) } }
  private val lastButtonCoords: MutableList<LayoutCoordinates?> = MutableList(rowIndex.size) { null }
  private var barCoords: LayoutCoordinates? = null
  var barWidthPx by mutableIntStateOf(0)
    private set

  fun reportBar(coords: LayoutCoordinates) {
    barCoords = coords
    barWidthPx = coords.size.width
    lastButtonCoords.forEachIndexed { i, bc ->
      if (bc != null && bc.isAttached && coords.isAttached) {
        tightBounds[i] = coords.localBoundingBoxOf(bc, clipBounds = false)
      }
    }
  }

  fun reportButton(
    index: Int,
    coords: LayoutCoordinates,
  ) {
    if (index !in lastButtonCoords.indices) return
    lastButtonCoords[index] = coords
    val bar = barCoords
    if (bar != null && bar.isAttached && coords.isAttached) {
      tightBounds[index] = bar.localBoundingBoxOf(coords, clipBounds = false)
    }
  }
}

internal fun computeArmedZones(
  tightBounds: List<Rect>,
  rowIndex: List<Int>,
  barWidthPx: Float,
): List<Rect>? {
  if (tightBounds.isEmpty() || tightBounds.size != rowIndex.size) return null
  if (tightBounds.any { it.width <= 0f || it.height <= 0f }) return null
  if (barWidthPx <= 0f) return null
  val zones = MutableList(tightBounds.size) { Rect.Zero }
  val byRow = rowIndex.indices.groupBy { rowIndex[it] }
  for ((_, indices) in byRow) {
    val sorted = indices.sortedBy { tightBounds[it].center.x }
    for ((order, idx) in sorted.withIndex()) {
      val rect = tightBounds[idx]
      val left =
        if (order == 0) {
          0f
        } else {
          val prev = sorted[order - 1]
          (tightBounds[idx].center.x + tightBounds[prev].center.x) / 2f
        }
      val right =
        if (order == sorted.lastIndex) {
          barWidthPx
        } else {
          val next = sorted[order + 1]
          (tightBounds[idx].center.x + tightBounds[next].center.x) / 2f
        }
      zones[idx] = Rect(left, rect.top, right, rect.bottom)
    }
  }
  return zones
}

@Composable
internal fun BarButton(
  spec: BarButtonSpec,
  index: Int,
  bounds: BarBoundsState,
  modifier: Modifier = Modifier,
) {
  val indication = LocalIndication.current
  val rippleModifier =
    if (spec.enabled) {
      Modifier.indication(spec.interactionSource, indication)
    } else {
      Modifier
    }
  Box(
    modifier =
      modifier
        .size(48.dp)
        .semantics(mergeDescendants = true) {
          contentDescription = spec.contentDescription
          role = Role.Button
          if (!spec.enabled) {
            disabled()
          } else {
            onClick(label = spec.label) {
              spec.onInvoke()
              true
            }
          }
        }.then(rippleModifier)
        .onGloballyPositioned { coords -> bounds.reportButton(index, coords) },
    contentAlignment = Alignment.Center,
  ) {
    val tint =
      if (spec.enabled) {
        LocalContentColor.current
      } else {
        LocalContentColor.current.copy(alpha = 0.38f)
      }
    Icon(
      imageVector = spec.icon,
      contentDescription = null,
      tint = tint,
    )
  }
}

@Composable
internal fun PreviewChip(
  armedIdx: Int?,
  specs: List<BarButtonSpec>,
  bounds: BarBoundsState,
  modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current
  var chipWidth by remember { mutableIntStateOf(0) }
  var chipHeight by remember { mutableIntStateOf(0) }
  val gapPx = with(density) { CHIP_VERTICAL_GAP.roundToPx() }

  var lastIdx by remember { mutableStateOf<Int?>(null) }
  if (armedIdx != null) lastIdx = armedIdx
  val displayIdx = armedIdx ?: lastIdx

  val displayLabel: String
  val displayEnabled: Boolean
  val displayCenterX: Float
  if (displayIdx != null && displayIdx in specs.indices && displayIdx in bounds.tightBounds.indices) {
    val spec = specs[displayIdx]
    val rect = bounds.tightBounds[displayIdx]
    displayLabel = spec.label
    displayEnabled = spec.enabled
    displayCenterX = rect.center.x
  } else {
    displayLabel = ""
    displayEnabled = true
    displayCenterX = 0f
  }

  val barWidthPx = bounds.barWidthPx
  val targetX =
    run {
      val unclamped = displayCenterX - chipWidth / 2f
      val maxX = max(0f, (barWidthPx - chipWidth).toFloat())
      unclamped.coerceIn(0f, maxX)
    }
  val animatedX by animateFloatAsState(
    targetValue = targetX,
    animationSpec = tween(CHIP_MOVE_MS),
    label = "chip-x",
  )

  Box(
    modifier =
      modifier
        .offset { IntOffset(animatedX.roundToInt(), -(chipHeight + gapPx)) }
        .onSizeChanged {
          chipWidth = it.width
          chipHeight = it.height
        }.semantics { hideFromAccessibility() },
  ) {
    AnimatedVisibility(
      visible = armedIdx != null,
      enter =
        fadeIn(tween(CHIP_FADE_MS)) +
          slideInVertically(tween(CHIP_FADE_MS)) {
            with(density) { CHIP_SLIDE_OFFSET.roundToPx() }
          },
      exit = fadeOut(tween(CHIP_FADE_MS)),
    ) {
      val labelColor =
        if (displayEnabled) {
          MaterialTheme.colorScheme.onSurface
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant
        }
      Surface(
        shape = MaterialTheme.shapes.small,
        tonalElevation = 2.dp,
      ) {
        Text(
          text = displayLabel,
          modifier =
            Modifier
              .widthIn(max = CHIP_MAX_WIDTH)
              .padding(horizontal = 12.dp, vertical = 6.dp),
          style = MaterialTheme.typography.labelMedium,
          color = labelColor,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}
