// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from enum_flags.djinni

#pragma once

#include "djinni_wasm.hpp"
#include "record_with_flags.hpp"

namespace djinni_generated {

struct NativeRecordWithFlags
{
    using CppType = ::testsuite::RecordWithFlags;
    using JsType = em::val;
    using Boxed = NativeRecordWithFlags;

    static CppType toCpp(const JsType& j);
    static JsType fromCpp(const CppType& c);
};

} // namespace djinni_generated