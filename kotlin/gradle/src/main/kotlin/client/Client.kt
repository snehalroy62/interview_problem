package com.css.challenge.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.random.Random
import kotlin.time.DurationUnit

/** Client is a client for fetching and solving challenge test problems. */
class Client(
    private val client: HttpClient,
    private val auth: String,
    private val endpoint: String
) {
    /**
     * newProblem fetches a new test problem from the server. The URL also works in a browser for
     * convenience.
     */
    suspend fun newProblem(name: String?, seed: Long?) : Problem {
        val url = URLBuilder("${endpoint}/interview/challenge/new").apply {
            parameters.append("auth", auth)
            parameters.append("name", name.orEmpty())
            parameters.append("seed", (seed ?: Random.nextLong()).toString())
        }.buildString()
        val resp = client.get(url)
        if (resp.status != HttpStatusCode.OK) {
            throw IOException("$url: ${resp.status}: ${resp.bodyAsText()}")
        }
        val id = resp.headers["x-test-id"].orEmpty()

        println("Fetched new test problem, id=$id: $url")
        return Problem(id, resp.body<String>().let { Json.decodeFromString<List<Order>>(it) })
    }

    @Serializable
    private data class Options(
        val rate: Long,
        val min: Long,
        val max: Long,
    )

    @Serializable
    private data class Solution(
        val options: Options,
        val actions: List<Action>,
    )

    /**
     * solve submits a sequence of actions and parameters as a solution to a test problem.
     * Returns test result.
     */
    suspend fun solve(testId: String, rate: Duration, min: Duration, max: Duration, actions: List<Action>) : String {
        val options = Options(rate.toLong(DurationUnit.MICROSECONDS), min.toLong(DurationUnit.MICROSECONDS), max.toLong(DurationUnit.MICROSECONDS))
        val solution = Solution(options, actions)

        val url = URLBuilder("${endpoint}/interview/challenge/solve").apply {
            parameters.append("auth", auth)
        }.buildString()
        val resp = client.post(url) {
            contentType(ContentType.Application.Json)
            headers { append("x-test-id", testId) }
            setBody(Json.encodeToString(solution))
        }
        if (resp.status != HttpStatusCode.OK) {
            throw IOException("$url: ${resp.status}: ${resp.bodyAsText()}")
        }

        return resp.bodyAsText()
    }
}
