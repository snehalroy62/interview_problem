package com.css.challenge.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client is a client for fetching and solving challenge test problems. */
public class Client {
  private static final Logger LOGGER = LoggerFactory.getLogger(Client.class);

  private final String endpoint;
  private final String auth;
  private final OkHttpClient client;

  public Client(String endpoint, String auth) {
    this.endpoint = endpoint;
    this.auth = auth;
    this.client =
        new OkHttpClient.Builder()
            .protocols(List.of(Protocol.HTTP_1_1))
            .build(); // To get HTTP status codes on errors
  }

  /**
   * newProblem fetches a new test problem from the server. The URL also works in a browser for
   * convenience.
   */
  public Problem newProblem(String name, long seed) throws IOException {
    if (seed == 0) {
      seed = new Random().nextLong();
    }

    HttpUrl url = HttpUrl.parse(endpoint + "/interview/challenge/new");
    if (url == null) {
      throw new IllegalArgumentException("Invalid endpoint: " + endpoint);
    }

    Request request =
        new Request.Builder()
            .url(
                url.newBuilder()
                    .addQueryParameter("auth", auth)
                    .addQueryParameter("name", name)
                    .addQueryParameter("seed", String.valueOf(seed))
                    .build())
            .build();

    try (Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException(
            "Unexpected code "
                + response.code()
                + ": "
                + response.message()
                + ": "
                + Objects.requireNonNull(response.body()).string());
      }
      String id = response.header("x-test-id");

      LOGGER.info("Fetched new test problem, id={}: {}", id, request.url());
      return new Problem(id, Order.parse(Objects.requireNonNull(response.body()).string()));
    }
  }

  private static class Options {
    public long rate;
    public long min;
    public long max;

    Options(Duration rate, Duration min, Duration max) {
      this.rate = TimeUnit.MILLISECONDS.toMicros(rate.toMillis());
      this.min = TimeUnit.MILLISECONDS.toMicros(min.toMillis());
      this.max = TimeUnit.MILLISECONDS.toMicros(max.toMillis());
    }
  }

  private static class Solution {
    public Options options;
    public List<Action> actions;

    Solution(Options options, List<Action> actions) {
      this.options = options;
      this.actions = actions;
    }

    String encode() throws IOException {
      return new ObjectMapper().writeValueAsString(this);
    }
  }

  /**
   * solveProblem submits a sequence of actions and parameters as a solution to a test problem.
   * Returns test result.
   */
  public String solveProblem(
      String testId, Duration rate, Duration min, Duration max, List<Action> actions)
      throws IOException {
    Solution solution = new Solution(new Options(rate, min, max), actions);

    HttpUrl url = HttpUrl.parse(endpoint + "/interview/challenge/solve");
    if (url == null) {
      throw new IllegalArgumentException("Invalid endpoint: " + endpoint);
    }
    Request request =
        new Request.Builder()
            .url(url.newBuilder().addQueryParameter("auth", auth).build())
            .addHeader("Content-Type", "application/json")
            .addHeader("x-test-id", testId)
            .post(RequestBody.create(MediaType.get("application/json"), solution.encode()))
            .build();

    try (Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException(
            "Unexpected code "
                + response.code()
                + ": "
                + response.message()
                + ": "
                + Objects.requireNonNull(response.body()).string());
      }
      return Objects.requireNonNull(response.body()).string();
    }
  }
}
