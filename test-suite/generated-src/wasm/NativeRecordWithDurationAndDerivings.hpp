// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from duration.djinni

#pragma once

#include "djinni_wasm.hpp"
#include "record_with_duration_and_derivings.hpp"

namespace djinni_generated {

struct NativeRecordWithDurationAndDerivings
{
    using CppType = ::testsuite::RecordWithDurationAndDerivings;
    using JsType = em::val;
    using Boxed = NativeRecordWithDurationAndDerivings;

    static CppType toCpp(const JsType& j);
    static JsType fromCpp(const CppType& c);
};

} // namespace djinni_generated