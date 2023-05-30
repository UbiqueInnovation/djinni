// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from djinni_perf_benchmark.djinni

#include "NativeObjectNative.hpp"  // my header

namespace djinni_generated {

NativeObjectNative::NativeObjectNative() : ::djinni::JniInterface<::snapchat::djinni::benchmark::ObjectNative, NativeObjectNative>("com/snapchat/djinni/benchmark/ObjectNative$CppProxy") {}

NativeObjectNative::~NativeObjectNative() = default;


CJNIEXPORT void JNICALL Java_com_snapchat_djinni_benchmark_ObjectNative_00024CppProxy_nativeDestroy(JNIEnv* jniEnv, jobject /*this*/, jlong nativeRef)
{
    try {
        delete reinterpret_cast<::djinni::CppProxyHandle<::snapchat::djinni::benchmark::ObjectNative>*>(nativeRef);
    } JNI_TRANSLATE_EXCEPTIONS_RETURN(jniEnv, )
}

CJNIEXPORT void JNICALL Java_com_snapchat_djinni_benchmark_ObjectNative_00024CppProxy_native_1baseline(JNIEnv* jniEnv, jobject /*this*/, jlong nativeRef)
{
    try {
        const auto& ref = ::djinni::objectFromHandleAddress<::snapchat::djinni::benchmark::ObjectNative>(nativeRef);
        ref->baseline();
    } JNI_TRANSLATE_EXCEPTIONS_RETURN(jniEnv, )
}

} // namespace djinni_generated
