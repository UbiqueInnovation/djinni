// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from date.djinni

#pragma once

#include <chrono>
#include <string>
#include <unordered_map>
#include <utility>

namespace testsuite {

struct MapDateRecord final {
    std::unordered_map<std::string, std::chrono::system_clock::time_point> dates_by_id;

    //NOLINTNEXTLINE(google-explicit-constructor)
    MapDateRecord(std::unordered_map<std::string, std::chrono::system_clock::time_point> dates_by_id_)
    : dates_by_id(std::move(dates_by_id_))
    {}
};

} // namespace testsuite
