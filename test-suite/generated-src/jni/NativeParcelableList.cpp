// AUTOGENERATED FILE - DO NOT MODIFY!
// This file generated by Djinni from parcelable.djinni

#include "NativeParcelableList.hpp"  // my header
#include "Marshal.hpp"

namespace djinni_generated {

NativeParcelableList::NativeParcelableList() = default;

NativeParcelableList::~NativeParcelableList() = default;

auto NativeParcelableList::fromCpp(JNIEnv* jniEnv, const CppType& c) -> ::djinni::LocalRef<JniType> {
    const auto& data = ::djinni::JniClass<NativeParcelableList>::get();
    auto r = ::djinni::LocalRef<JniType>{jniEnv->NewObject(data.clazz.get(), data.jconstructor,
                                                           ::djinni::get(::djinni::List<::djinni::String>::fromCpp(jniEnv, c.l)))};
    ::djinni::jniExceptionCheck(jniEnv);
    return r;
}

auto NativeParcelableList::toCpp(JNIEnv* jniEnv, JniType j) -> CppType {
    ::djinni::JniLocalScope jscope(jniEnv, 2);
    assert(j != nullptr);
    const auto& data = ::djinni::JniClass<NativeParcelableList>::get();
    return {::djinni::List<::djinni::String>::toCpp(jniEnv, jniEnv->GetObjectField(j, data.field_mL))};
}

}  // namespace djinni_generated