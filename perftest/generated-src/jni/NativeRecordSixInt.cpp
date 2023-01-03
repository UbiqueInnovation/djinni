// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from djinni_perf_benchmark.djinni

#include "NativeRecordSixInt.hpp"  // my header
#include "Marshal.hpp"

namespace djinni_generated {

NativeRecordSixInt::NativeRecordSixInt() = default;

NativeRecordSixInt::~NativeRecordSixInt() = default;

auto NativeRecordSixInt::fromCpp(JNIEnv* jniEnv, const CppType& c) -> ::djinni::LocalRef<JniType> {
    const auto& data = ::djinni::JniClass<NativeRecordSixInt>::get();
    auto r = ::djinni::LocalRef<JniType>{jniEnv->NewObject(data.clazz.get(), data.jconstructor,
                                                           ::djinni::get(::djinni::I64::fromCpp(jniEnv, c.i1)),
                                                           ::djinni::get(::djinni::I64::fromCpp(jniEnv, c.i2)),
                                                           ::djinni::get(::djinni::I64::fromCpp(jniEnv, c.i3)),
                                                           ::djinni::get(::djinni::I64::fromCpp(jniEnv, c.i4)),
                                                           ::djinni::get(::djinni::I64::fromCpp(jniEnv, c.i5)),
                                                           ::djinni::get(::djinni::I64::fromCpp(jniEnv, c.i6)))};
    ::djinni::jniExceptionCheck(jniEnv);
    return r;
}

auto NativeRecordSixInt::toCpp(JNIEnv* jniEnv, JniType j) -> CppType {
    ::djinni::JniLocalScope jscope(jniEnv, 7);
    assert(j != nullptr);
    const auto& data = ::djinni::JniClass<NativeRecordSixInt>::get();
    return {::djinni::I64::toCpp(jniEnv, jniEnv->GetLongField(j, data.field_mI1)),
            ::djinni::I64::toCpp(jniEnv, jniEnv->GetLongField(j, data.field_mI2)),
            ::djinni::I64::toCpp(jniEnv, jniEnv->GetLongField(j, data.field_mI3)),
            ::djinni::I64::toCpp(jniEnv, jniEnv->GetLongField(j, data.field_mI4)),
            ::djinni::I64::toCpp(jniEnv, jniEnv->GetLongField(j, data.field_mI5)),
            ::djinni::I64::toCpp(jniEnv, jniEnv->GetLongField(j, data.field_mI6))};
}

} // namespace djinni_generated
