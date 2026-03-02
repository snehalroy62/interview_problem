#include "client.h"

#include <random>
#include <iostream>

#include <cpr/cpr.h>
#include <nlohmann/json.hpp>

using json = nlohmann::json;

Problem Client::newProblem(const std::string& name, long seed) {
    if (seed == 0) {
        std::random_device rd;
        std::mt19937_64 generator(rd());
        std::uniform_int_distribution<long> distribution;

        seed = distribution(generator);
    }
    std::string url = endpoint + "/interview/challenge/new";
    auto response = cpr::Get(
        cpr::Url{url},
        cpr::Parameters{{"auth", auth}, {"name", name}, {"seed", std::to_string(seed)}
        }
    );
    if (response.status_code != 200) {
        throw std::runtime_error(response.url.str() + ": " + response.status_line + ": " + response.text);
    }

    auto id = response.header["x-test-id"];
    std::cout << "Fetched new test problem, id=" << id << ": " << response.url.str() << std::endl;

    return Problem(id, json::parse(response.text).get<std::vector<Order>>());
}

struct Options {
    long rate; // inverse rate in microseconds
    long min;  // min pickup in microseconds
    long max;  // max pickup in microseconds

    Options() = default;
    Options(std::chrono::microseconds rate, std::chrono::microseconds min, std::chrono::microseconds max)
        : rate(rate.count()), min(min.count()), max(max.count()) {}

    NLOHMANN_DEFINE_TYPE_INTRUSIVE(Options, rate, min, max);
};

struct Solution {
    Options options;
    std::vector<Action> actions;

    Solution(const Options& options, const std::vector<Action>& actions)
     : options(options), actions(actions) {}

    NLOHMANN_DEFINE_TYPE_INTRUSIVE(Solution, options, actions);
};

std::string Client::solve(const std::string& testId, std::chrono::microseconds rate, std::chrono::microseconds min, std::chrono::microseconds max, const std::vector<Action>& actions) {
    std::string url = endpoint + "/interview/challenge/solve";
    auto solution = json(Solution(Options(rate, min, max), actions));
    auto response = cpr::Post(
        cpr::Url{url},
        cpr::Parameters{{"auth", auth}},
        cpr::Header{{"Content-Type", "application/json"}, {"x-test-id", testId}},
        cpr::Body{solution.dump()}
    );
    if (response.status_code != 200) {
        throw std::runtime_error(response.url.str() + ": " + response.status_line + ": " + response.text);
    }

    return response.text; 
}

