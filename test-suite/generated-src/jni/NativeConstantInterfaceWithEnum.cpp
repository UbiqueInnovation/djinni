// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from constant_enum.djinni

#include "NativeConstantInterfaceWithEnum.hpp"  // my header
#include "NativeConstantEnum.hpp"

namespace djinni_generated {

NativeConstantInterfaceWithEnum::NativeConstantInterfaceWithEnum() : ::djinni::JniInterface<::testsuite::ConstantInterfaceWithEnum, NativeConstantInterfaceWithEnum>("com/dropbox/djinni/test/ConstantInterfaceWithEnum$CppProxy") {}

NativeConstantInterfaceWithEnum::~NativeConstantInterfaceWithEnum() = default;


CJNIEXPORT void JNICALL Java_com_dropbox_djinni_test_ConstantInterfaceWithEnum_00024CppProxy_nativeDestroy(JNIEnv* jniEnv, jobject /*this*/, jlong nativeRef)
{
    try {
        delete reinterpret_cast<::djinni::CppProxyHandle<::testsuite::ConstantInterfaceWithEnum>*>(nativeRef);
    } JNI_TRANSLATE_EXCEPTIONS_RETURN(jniEnv, )
}

} // namespace djinni_generated
