// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from proto.djinni

#pragma once

#include "RecordWithEmbeddedProto.hpp"
#include "djinni_wasm.hpp"

namespace djinni_generated {

struct NativeRecordWithEmbeddedProto
{
    using CppType = ::testsuite::RecordWithEmbeddedProto;
    using JsType = em::val;
    using Boxed = NativeRecordWithEmbeddedProto;

    static CppType toCpp(const JsType& j);
    static JsType fromCpp(const CppType& c);
};

} // namespace djinni_generated
