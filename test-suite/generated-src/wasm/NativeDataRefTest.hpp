// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from data_ref_view.djinni

#pragma once

#include "DataRefTest.hpp"
#include "djinni_wasm.hpp"

namespace djinni_generated {

struct NativeDataRefTest : ::djinni::JsInterface<::testsuite::DataRefTest, NativeDataRefTest> {
    using CppType = std::shared_ptr<::testsuite::DataRefTest>;
    using CppOptType = std::shared_ptr<::testsuite::DataRefTest>;
    using JsType = em::val;
    using Boxed = NativeDataRefTest;

    static CppType toCpp(JsType j) { return _fromJs(j); }
    static JsType fromCppOpt(const CppOptType& c) { return {_toJs(c)}; }
    static JsType fromCpp(const CppType& c) {
        ::djinni::checkForNull(c.get(), "NativeDataRefTest::fromCpp");
        return fromCppOpt(c);
    }

    static em::val cppProxyMethods();

    static void sendData(const CppType& self, const em::val& w_data);
    static em::val retriveAsBin(const CppType& self);
    static void sendMutableData(const CppType& self, const em::val& w_data);
    static em::val generateData(const CppType& self);
    static em::val dataFromVec(const CppType& self);
    static em::val dataFromStr(const CppType& self);
    static em::val sendDataView(const CppType& self, const em::val& w_data);
    static em::val recvDataView(const CppType& self);
    static em::val create();

};

} // namespace djinni_generated