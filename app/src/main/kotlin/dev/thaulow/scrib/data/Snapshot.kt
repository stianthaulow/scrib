package dev.thaulow.scrib.data

import kotlinx.serialization.Serializable

@Serializable
data class Snapshot(
    val text: String,
    val cursorStart: Int,
    val cursorEnd: Int,
)

@Serializable
data class UndoStackDto(
    val entries: List<Snapshot>,
    val index: Int,
)
