#pragma once

#include <string>
#include <chrono>
#include <nlohmann/json.hpp>

// Action is a json-friendly representation of an action.
struct Action {
    long timestamp;     // unix timestamp in microseconds
    std::string id;     // order id
    std::string action; // place, move, pickup or discard
    std::string target; // heater, cooler or shelf. Target is the destination for move

    Action() = default;
    Action(const std::chrono::steady_clock::time_point& timestamp, const std::string& id, const std::string& action, const std::string& target)
        : timestamp(std::chrono::duration_cast<std::chrono::microseconds>(timestamp.time_since_epoch()).count()), id(id), action(action), target(target) {}

    NLOHMANN_DEFINE_TYPE_INTRUSIVE(Action, timestamp, id, action, target);
};

inline std::ostream& operator<<(std::ostream& out, const Action& action) {
    out << nlohmann::json(action);
    return out;
}
