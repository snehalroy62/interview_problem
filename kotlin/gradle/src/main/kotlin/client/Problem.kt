package com.css.challenge.client

/** Problem represents a test problem */
data class Problem(
    val testId: String,
    val orders: List<Order>
)
