// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from primtypes.djinni

#pragma once

#include "assorted_primitives.hpp"
#include "djinni_wasm.hpp"

namespace djinni_generated {

struct NativeAssortedPrimitives
{
    using CppType = ::testsuite::AssortedPrimitives;
    using JsType = em::val;
    using Boxed = NativeAssortedPrimitives;

    static CppType toCpp(const JsType& j);
    static JsType fromCpp(const CppType& c);
};

} // namespace djinni_generated
