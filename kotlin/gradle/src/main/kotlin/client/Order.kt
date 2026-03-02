package com.css.challenge.client

import kotlinx.serialization.Serializable

@Serializable
data class Order(
    val id: String,     // order id
    val name: String,   // food name
    val temp: String,   // ideal temperature
    val price: Int = 0, // price in dollars
    val freshness: Int  // freshness in seconds
)
