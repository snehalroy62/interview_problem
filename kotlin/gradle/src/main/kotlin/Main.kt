package com.css.challenge

import com.css.challenge.client.Action
import com.css.challenge.client.COOLER
import com.css.challenge.client.Client
import com.css.challenge.client.PLACE
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.long
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import kotlin.time.Clock.System.now
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class Main : CliktCommand() {
    private val endpoint by option().default("https://api.cloudkitchens.com").help("Problem server endpoint")
    private val auth by option().help("Authentication token (required)").required()
    private val name by option().help("Problem name. Leave blank (optional)")
    private val seed by option().long().help("Problem seed (random if unset)")

    private val rate by option().convert { Duration.parse(it) }.default(500.milliseconds).help("Inverse order rate")
    private val min by option().convert { Duration.parse(it) }.default(4.seconds).help("Minimum pickup time")
    private val max by option().convert { Duration.parse(it) }.default(8.seconds).help("Maximum pickup time")

    override fun run() = runBlocking {
        try {
            val client = Client(HttpClient(CIO), auth, endpoint)
            val problem = client.newProblem(name, seed)

            // ------ Execution harness logic goes here using rate, min and max ----

            val actions = mutableListOf<Action>()
            for (order in problem.orders) {
                println("Received: $order")

                actions.add(Action(now(), order.id, PLACE, COOLER))
                delay(rate)
            }

            // ----------------------------------------------------------------------

            val result = client.solve(problem.testId, rate, min, max, actions)
            println("Result: $result")

        } catch (e: IOException) {
            println("Execution failed: ${e.message}")
        }
    }
}

fun main(args: Array<String>) = Main().main(args)
