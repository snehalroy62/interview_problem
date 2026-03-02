#pragma once

#include <string>

#include "order.h"

// Problem represents a test problem
struct Problem {
    Problem(const std::string& testId, const std::vector<Order>& orders)
        : testId(testId), orders(orders) {}

    std::string testId;
    std::vector<Order> orders;
};
