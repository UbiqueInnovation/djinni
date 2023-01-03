// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from varnames.djinni

#pragma once

#include "_varname_interface_.hpp"
#include "djinni_wasm.hpp"

namespace djinni_generated {

struct NativeVarnameInterface : ::djinni::JsInterface<::testsuite::VarnameInterface, NativeVarnameInterface> {
    using CppType = std::shared_ptr<::testsuite::VarnameInterface>;
    using CppOptType = std::shared_ptr<::testsuite::VarnameInterface>;
    using JsType = em::val;
    using Boxed = NativeVarnameInterface;

    static CppType toCpp(JsType j) { return _fromJs(j); }
    static JsType fromCppOpt(const CppOptType& c) { return {_toJs(c)}; }
    static JsType fromCpp(const CppType& c) {
        ::djinni::checkForNull(c.get(), "NativeVarnameInterface::fromCpp");
        return fromCppOpt(c);
    }

    static em::val cppProxyMethods();

    static em::val _rmethod_(const CppType& self, const em::val& w__r_arg_);
    static em::val _imethod_(const CppType& self, const em::val& w__i_arg_);

};

} // namespace djinni_generated
