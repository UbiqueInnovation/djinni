// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from map.djinni

#include "NativeMapListRecord.hpp"  // my header

namespace djinni_generated {

auto NativeMapListRecord::toCpp(const JsType& j) -> CppType {
    return {::djinni::List<::djinni::Map<::djinni::String, ::djinni::I64>>::Boxed::toCpp(j["mapList"])};
}
auto NativeMapListRecord::fromCpp(const CppType& c) -> JsType {
    em::val js = em::val::object();
    js.set("mapList", ::djinni::List<::djinni::Map<::djinni::String, ::djinni::I64>>::Boxed::fromCpp(c.map_list));
    return js;
}

} // namespace djinni_generated