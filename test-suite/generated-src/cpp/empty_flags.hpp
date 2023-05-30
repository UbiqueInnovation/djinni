// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from enum_flags.djinni

#pragma once

#include <functional>

namespace testsuite {

enum class empty_flags : int32_t {
    NONE = 0,
    ALL = 0,
};
constexpr empty_flags operator|(empty_flags lhs, empty_flags rhs) noexcept {
    return static_cast<empty_flags>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
inline empty_flags& operator|=(empty_flags& lhs, empty_flags rhs) noexcept {
    return lhs = lhs | rhs;
}
constexpr empty_flags operator&(empty_flags lhs, empty_flags rhs) noexcept {
    return static_cast<empty_flags>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
inline empty_flags& operator&=(empty_flags& lhs, empty_flags rhs) noexcept {
    return lhs = lhs & rhs;
}
constexpr empty_flags operator^(empty_flags lhs, empty_flags rhs) noexcept {
    return static_cast<empty_flags>(static_cast<int32_t>(lhs) ^ static_cast<int32_t>(rhs));
}
inline empty_flags& operator^=(empty_flags& lhs, empty_flags rhs) noexcept {
    return lhs = lhs ^ rhs;
}
constexpr empty_flags operator~(empty_flags x) noexcept {
    return static_cast<empty_flags>(~static_cast<int32_t>(x));
}

} // namespace testsuite

namespace std {

template <>
struct hash<::testsuite::empty_flags> {
    size_t operator()(::testsuite::empty_flags type) const {
        return std::hash<int32_t>()(static_cast<int32_t>(type));
    }
};

} // namespace std
