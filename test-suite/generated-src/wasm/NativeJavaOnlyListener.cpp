// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from single_language_interfaces.djinni

#include "NativeJavaOnlyListener.hpp"  // my header

namespace djinni_generated {


EMSCRIPTEN_BINDINGS(testsuite_java_only_listener) {
    ::djinni::DjinniClass_<::testsuite::JavaOnlyListener>("testsuite_JavaOnlyListener", "testsuite.JavaOnlyListener")
        .smart_ptr<std::shared_ptr<::testsuite::JavaOnlyListener>>("testsuite_JavaOnlyListener")
        .function("nativeDestroy", &NativeJavaOnlyListener::nativeDestroy)
        ;
}

} // namespace djinni_generated