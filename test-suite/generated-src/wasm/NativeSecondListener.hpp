// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from multiple_inheritance.djinni

#pragma once

#include "djinni_wasm.hpp"
#include "second_listener.hpp"

namespace djinni_generated {

struct NativeSecondListener : ::djinni::JsInterface<::testsuite::SecondListener, NativeSecondListener> {
    using CppType = std::shared_ptr<::testsuite::SecondListener>;
    using CppOptType = std::shared_ptr<::testsuite::SecondListener>;
    using JsType = em::val;
    using Boxed = NativeSecondListener;

    static CppType toCpp(JsType j) { return _fromJs(j); }
    static JsType fromCppOpt(const CppOptType& c) { return {_toJs(c)}; }
    static JsType fromCpp(const CppType& c) {
        ::djinni::checkForNull(c.get(), "NativeSecondListener::fromCpp");
        return fromCppOpt(c);
    }


};

} // namespace djinni_generated
