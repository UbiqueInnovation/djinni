// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from constant_enum.djinni

#include "NativeConstantEnum.hpp"  // my header
#include <mutex>

namespace djinni_generated {

namespace {
    EM_JS(void, djinni_init_testsuite_constant_enum_consts, (), {
        Module.testsuite_ConstantEnum =  {
            SOME_VALUE : 0,
            SOME_OTHER_VALUE : 1,
        }
    })
}

void NativeConstantEnum::staticInitializeConstants() {
    static std::once_flag initOnce;
    std::call_once(initOnce, [] {
        djinni_init_testsuite_constant_enum_consts();
        ::djinni::djinni_register_name_in_ns("testsuite_ConstantEnum", "testsuite.ConstantEnum");
    });
}

EMSCRIPTEN_BINDINGS(testsuite_constant_enum) {
    NativeConstantEnum::staticInitializeConstants();
}

} // namespace djinni_generated