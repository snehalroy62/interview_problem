package com.css.challenge;

import com.css.challenge.client.Action;
import com.css.challenge.client.Client;
import com.css.challenge.client.Order;
import com.css.challenge.client.Problem;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "challenge", showDefaultValues = true)
public class Main implements Runnable {
  private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

  static {
    System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tF %1$tT: %5$s %n");
  }

  @Option(names = "--endpoint", description = "Problem server endpoint")
  String endpoint = "https://api.cloudkitchens.com";

  @Option(names = "--auth", description = "Authentication token (required)")
  String auth = "";

  @Option(names = "--name", description = "Problem name. Leave blank (optional)")
  String name = "";

  @Option(names = "--seed", description = "Problem seed (random if zero)")
  long seed = 0;

  @Option(names = "--rate", description = "Inverse order rate")
  Duration rate = Duration.ofMillis(500);

  @Option(names = "--min", description = "Minimum pickup time")
  Duration min = Duration.ofSeconds(4);

  @Option(names = "--max", description = "Maximum pickup time")
  Duration max = Duration.ofSeconds(8);

  @Override
  public void run() {
    try {
      Client client = new Client(endpoint, auth);
      Problem problem = client.newProblem(name, seed);

      // ------ Execution harness logic goes here using rate, min and max ----

      Kitchen kitchen = new Kitchen(min, max);
      for (Order order : problem.getOrders()) {
        kitchen.receiveOrder(order);
        Thread.sleep(rate.toMillis());
      }

      kitchen.waitUntilDone();
      List<Action> actions = kitchen.getActions();

      // ----------------------------------------------------------------------

      String result = client.solveProblem(problem.getTestId(), rate, min, max, actions);
      LOGGER.info("Result: {}", result);

    } catch (IOException | InterruptedException e) {
      LOGGER.error("Execution failed: {}", e.getMessage());
    }
  }

  public static void main(String[] args) {
    new CommandLine(new Main()).execute(args);
  }
}
