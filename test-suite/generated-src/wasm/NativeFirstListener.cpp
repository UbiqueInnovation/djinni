// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from multiple_inheritance.djinni

#include "NativeFirstListener.hpp"  // my header

namespace djinni_generated {


EMSCRIPTEN_BINDINGS(testsuite_first_listener) {
    ::djinni::DjinniClass_<::testsuite::FirstListener>("testsuite_FirstListener", "testsuite.FirstListener")
        .smart_ptr<std::shared_ptr<::testsuite::FirstListener>>("testsuite_FirstListener")
        .function("nativeDestroy", &NativeFirstListener::nativeDestroy)
        ;
}

} // namespace djinni_generated