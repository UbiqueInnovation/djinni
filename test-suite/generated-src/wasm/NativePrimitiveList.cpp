// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from primitive_list.djinni

#include "NativePrimitiveList.hpp"  // my header

namespace djinni_generated {

auto NativePrimitiveList::toCpp(const JsType& j) -> CppType {
    return {::djinni::List<::djinni::I64>::Boxed::toCpp(j["list"])};
}
auto NativePrimitiveList::fromCpp(const CppType& c) -> JsType {
    em::val js = em::val::object();
    js.set("list", ::djinni::List<::djinni::I64>::Boxed::fromCpp(c.list));
    return js;
}

} // namespace djinni_generated
