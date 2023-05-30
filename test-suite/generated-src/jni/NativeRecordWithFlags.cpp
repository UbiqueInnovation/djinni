// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from enum_flags.djinni

#include "NativeRecordWithFlags.hpp"  // my header
#include "NativeAccessFlags.hpp"

namespace djinni_generated {

NativeRecordWithFlags::NativeRecordWithFlags() = default;

NativeRecordWithFlags::~NativeRecordWithFlags() = default;

auto NativeRecordWithFlags::fromCpp(JNIEnv* jniEnv, const CppType& c) -> ::djinni::LocalRef<JniType> {
    const auto& data = ::djinni::JniClass<NativeRecordWithFlags>::get();
    auto r = ::djinni::LocalRef<JniType>{jniEnv->NewObject(data.clazz.get(), data.jconstructor,
                                                           ::djinni::get(::djinni_generated::NativeAccessFlags::fromCpp(jniEnv, c.access)))};
    ::djinni::jniExceptionCheck(jniEnv);
    return r;
}

auto NativeRecordWithFlags::toCpp(JNIEnv* jniEnv, JniType j) -> CppType {
    ::djinni::JniLocalScope jscope(jniEnv, 2);
    assert(j != nullptr);
    const auto& data = ::djinni::JniClass<NativeRecordWithFlags>::get();
    return {::djinni_generated::NativeAccessFlags::toCpp(jniEnv, jniEnv->GetObjectField(j, data.field_mAccess))};
}

} // namespace djinni_generated
