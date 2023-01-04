// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from outcome.djinni

#include "NativeTestOutcome.hpp"  // my header
#include "NativeNestedOutcome.hpp"
#include "Outcome_wasm.hpp"

namespace djinni_generated {

em::val NativeTestOutcome::cppProxyMethods() {
    static const em::val methods = em::val::array(std::vector<std::string> {
    });
    return methods;
}

em::val NativeTestOutcome::getSuccessOutcome() {
    try {
        auto r = ::testsuite::TestOutcome::getSuccessOutcome();
        return ::djinni::Outcome<::djinni::String, ::djinni::I32>::fromCpp(r);
    }
    catch(const std::exception& e) {
        return ::djinni::ExceptionHandlingTraits<::djinni::Outcome<::djinni::String, ::djinni::I32>>::handleNativeException(e);
    }
}
em::val NativeTestOutcome::getErrorOutcome() {
    try {
        auto r = ::testsuite::TestOutcome::getErrorOutcome();
        return ::djinni::Outcome<::djinni::String, ::djinni::I32>::fromCpp(r);
    }
    catch(const std::exception& e) {
        return ::djinni::ExceptionHandlingTraits<::djinni::Outcome<::djinni::String, ::djinni::I32>>::handleNativeException(e);
    }
}
std::string NativeTestOutcome::putSuccessOutcome(const em::val& w_x) {
    try {
        auto r = ::testsuite::TestOutcome::putSuccessOutcome(::djinni::Outcome<::djinni::String, ::djinni::I32>::toCpp(w_x));
        return ::djinni::String::fromCpp(r);
    }
    catch(const std::exception& e) {
        return ::djinni::ExceptionHandlingTraits<::djinni::String>::handleNativeException(e);
    }
}
int32_t NativeTestOutcome::putErrorOutcome(const em::val& w_x) {
    try {
        auto r = ::testsuite::TestOutcome::putErrorOutcome(::djinni::Outcome<::djinni::String, ::djinni::I32>::toCpp(w_x));
        return ::djinni::I32::fromCpp(r);
    }
    catch(const std::exception& e) {
        return ::djinni::ExceptionHandlingTraits<::djinni::I32>::handleNativeException(e);
    }
}
em::val NativeTestOutcome::getNestedSuccessOutcome() {
    try {
        auto r = ::testsuite::TestOutcome::getNestedSuccessOutcome();
        return ::djinni_generated::NativeNestedOutcome::fromCpp(r);
    }
    catch(const std::exception& e) {
        return ::djinni::ExceptionHandlingTraits<::djinni_generated::NativeNestedOutcome>::handleNativeException(e);
    }
}
em::val NativeTestOutcome::getNestedErrorOutcome() {
    try {
        auto r = ::testsuite::TestOutcome::getNestedErrorOutcome();
        return ::djinni_generated::NativeNestedOutcome::fromCpp(r);
    }
    catch(const std::exception& e) {
        return ::djinni::ExceptionHandlingTraits<::djinni_generated::NativeNestedOutcome>::handleNativeException(e);
    }
}
int32_t NativeTestOutcome::putNestedSuccessOutcome(const em::val& w_x) {
    try {
        auto r = ::testsuite::TestOutcome::putNestedSuccessOutcome(::djinni_generated::NativeNestedOutcome::toCpp(w_x));
        return ::djinni::I32::fromCpp(r);
    }
    catch(const std::exception& e) {
        return ::djinni::ExceptionHandlingTraits<::djinni::I32>::handleNativeException(e);
    }
}
std::string NativeTestOutcome::putNestedErrorOutcome(const em::val& w_x) {
    try {
        auto r = ::testsuite::TestOutcome::putNestedErrorOutcome(::djinni_generated::NativeNestedOutcome::toCpp(w_x));
        return ::djinni::String::fromCpp(r);
    }
    catch(const std::exception& e) {
        return ::djinni::ExceptionHandlingTraits<::djinni::String>::handleNativeException(e);
    }
}

EMSCRIPTEN_BINDINGS(testsuite_test_outcome) {
    ::djinni::DjinniClass_<::testsuite::TestOutcome>("testsuite_TestOutcome", "testsuite.TestOutcome")
        .smart_ptr<std::shared_ptr<::testsuite::TestOutcome>>("testsuite_TestOutcome")
        .function("nativeDestroy", &NativeTestOutcome::nativeDestroy)
        .class_function("getSuccessOutcome", NativeTestOutcome::getSuccessOutcome)
        .class_function("getErrorOutcome", NativeTestOutcome::getErrorOutcome)
        .class_function("putSuccessOutcome", NativeTestOutcome::putSuccessOutcome)
        .class_function("putErrorOutcome", NativeTestOutcome::putErrorOutcome)
        .class_function("getNestedSuccessOutcome", NativeTestOutcome::getNestedSuccessOutcome)
        .class_function("getNestedErrorOutcome", NativeTestOutcome::getNestedErrorOutcome)
        .class_function("putNestedSuccessOutcome", NativeTestOutcome::putNestedSuccessOutcome)
        .class_function("putNestedErrorOutcome", NativeTestOutcome::putNestedErrorOutcome)
        ;
}

} // namespace djinni_generated