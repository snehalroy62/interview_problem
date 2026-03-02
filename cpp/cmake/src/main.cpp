#include <iostream>
#include <chrono>
#include <signal.h>
#include <thread>
#include <argparse/argparse.hpp>

#include "client/client.h"

struct Args : public argparse::Args {
  std::string& endpoint = kwarg("endpoint", "Problem server endpoint").set_default("https://api.cloudkitchens.com");
  std::string& auth = kwarg("auth", "Authentication token");
  std::string& name = kwarg("name", "Problem name. Leave blank (optional)").set_default("");
  long& seed = kwarg("seed", "Problem seed (random if zero)").set_default(0);

  long& rate = kwarg("rate", "Inverse order rate (milliseconds)").set_default(500);
  long& min = kwarg("min", "Minimum pickup time (seconds)").set_default(4);
  long& max = kwarg("max", "Maximum pickup time (seconds)").set_default(8);
};

void interrupted(int s)
{
    std::quick_exit(1);
}

int main(int argc, char** argv) {
  signal(SIGINT, interrupted);
  try {
    auto args = argparse::parse<Args>(argc, argv);

    auto rate = std::chrono::milliseconds(args.rate);
    auto min = std::chrono::seconds(args.min);
    auto max = std::chrono::seconds(args.max);

    Client client(args.endpoint, args.auth);
    auto problem = client.newProblem(args.name, args.seed);

    // ------ Execution harness logic goes here using rate, min and max ----

    std::vector<Action> actions;
    for (auto order : problem.orders) {
      std::cout << "Received: " << order << std::endl;
      actions.emplace_back(Action(std::chrono::steady_clock::now(), order.id, "place", "cooler"));

      std::this_thread::sleep_for(rate);
    }

    // ----------------------------------------------------------------------

    auto result = client.solve(problem.testId, rate, min, max, actions);
    std::cout << "Result: " << result << std::endl;
    return 0;

  } catch (const std::exception& e) {
    std::cout << "Execution failed: " << e.what() << std::endl;
    return 1;
  }
}
