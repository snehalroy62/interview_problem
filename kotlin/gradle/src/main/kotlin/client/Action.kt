package com.css.challenge.client

import kotlin.time.Instant
import kotlinx.serialization.Serializable

const val PLACE = "place"
const val PICKUP = "pickup"
const val MOVE = "move"
const val DISCARD = "discard"

const val HEATER = "heater"
const val COOLER = "cooler"
const val SHELF = "shelf"

@Serializable
data class Action(
    val timestamp: Long, // unix timestamp in microseconds
    val id: String,      // order id
    val action: String,  // place, move, pickup or discard
    val target: String,  // heater, cooler or shelf. Target is the destination for move
) {
    constructor(timestamp: Instant, id: String, action: String, target: String) : this(timestamp.toEpochMilliseconds() * 1000L, id, action, target)
}
