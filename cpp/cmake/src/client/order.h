#pragma once

#include <string>
#include <nlohmann/json.hpp>

// Order is a json-friendly representation of an order.
struct Order {
    std::string id;   // order id
    std::string name; // food name
    std::string temp; // ideal temperature
    int price;        // price in dollars
    int freshness;    // freshness in seconds

    NLOHMANN_DEFINE_TYPE_INTRUSIVE(Order, id, name, temp, price, freshness);
};

inline std::ostream& operator<<(std::ostream& out, const Order& order) {
    out << nlohmann::json(order);
    return out;
}
