// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from multiple_inheritance.djinni

#include "NativeSecondListener.hpp"  // my header

namespace djinni_generated {


EMSCRIPTEN_BINDINGS(testsuite_second_listener) {
    ::djinni::DjinniClass_<::testsuite::SecondListener>("testsuite_SecondListener", "testsuite.SecondListener")
        .smart_ptr<std::shared_ptr<::testsuite::SecondListener>>("testsuite_SecondListener")
        .function("nativeDestroy", &NativeSecondListener::nativeDestroy)
        ;
}

} // namespace djinni_generated