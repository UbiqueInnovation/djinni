// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from function_prologue.djinni

#import "DBFunctionPrologueHelper+Private.h"
#import "DBFunctionPrologueHelper.h"
#import "DJICppWrapperCache+Private.h"
#import "DJIError.h"
#import "DJIMarshal+Private.h"
#include "../../handwritten-src/cpp/objcpp-prologue.hpp"
#include <exception>
#include <stdexcept>
#include <utility>

static_assert(__has_feature(objc_arc), "Djinni requires ARC to be enabled for this file");

@interface DBFunctionPrologueHelper ()

- (id)initWithCpp:(const std::shared_ptr<::testsuite::FunctionPrologueHelper>&)cppRef;

@end

@implementation DBFunctionPrologueHelper {
    ::djinni::CppProxyCache::Handle<std::shared_ptr<::testsuite::FunctionPrologueHelper>> _cppRefHandle;
}

- (id)initWithCpp:(const std::shared_ptr<::testsuite::FunctionPrologueHelper>&)cppRef
{
    if (self = [super init]) {
        _cppRefHandle.assign(cppRef);
    }
    return self;
}

+ (nonnull NSString *)foo {
    try {
        DJINNI_FUNCTION_PROLOGUE("FunctionPrologueHelper.foo");
        auto objcpp_result_ = ::testsuite::FunctionPrologueHelper::foo();
        return ::djinni::String::fromCpp(objcpp_result_);
    } DJINNI_TRANSLATE_EXCEPTIONS()
}

namespace djinni_generated {

auto FunctionPrologueHelper::toCpp(ObjcType objc) -> CppType
{
    if (!objc) {
        return nullptr;
    }
    return objc->_cppRefHandle.get();
}

auto FunctionPrologueHelper::fromCppOpt(const CppOptType& cpp) -> ObjcType
{
    if (!cpp) {
        return nil;
    }
    return ::djinni::get_cpp_proxy<DBFunctionPrologueHelper>(cpp);
}

} // namespace djinni_generated

@end
