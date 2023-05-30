// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from user_token.djinni

#import "DBUserToken+Private.h"
#import "DBUserToken.h"
#import "DJICppWrapperCache+Private.h"
#import "DJIError.h"
#import "DJIMarshal+Private.h"
#import "DJIObjcWrapperCache+Private.h"
#include <exception>
#include <stdexcept>
#include <utility>

static_assert(__has_feature(objc_arc), "Djinni requires ARC to be enabled for this file");

@interface DBUserTokenCppProxy : NSObject<DBUserToken>

- (id)initWithCpp:(const std::shared_ptr<::testsuite::UserToken>&)cppRef;

@end

@implementation DBUserTokenCppProxy {
    ::djinni::CppProxyCache::Handle<std::shared_ptr<::testsuite::UserToken>> _cppRefHandle;
}

- (id)initWithCpp:(const std::shared_ptr<::testsuite::UserToken>&)cppRef
{
    if (self = [super init]) {
        _cppRefHandle.assign(cppRef);
    }
    return self;
}

- (nonnull NSString *)whoami {
    try {
        auto objcpp_result_ = _cppRefHandle.get()->whoami();
        return ::djinni::String::fromCpp(objcpp_result_);
    } DJINNI_TRANSLATE_EXCEPTIONS()
}

namespace djinni_generated {

class UserToken::ObjcProxy final
: public ::testsuite::UserToken
, private ::djinni::ObjcProxyBase<ObjcType>
{
    friend class ::djinni_generated::UserToken;
public:
    using ObjcProxyBase::ObjcProxyBase;
    std::string whoami() override
    {
        @autoreleasepool {
            auto objcpp_result_ = [djinni_private_get_proxied_objc_object() whoami];
            return ::djinni::String::toCpp(objcpp_result_);
        }
    }
};

} // namespace djinni_generated

namespace djinni_generated {

auto UserToken::toCpp(ObjcType objc) -> CppType
{
    if (!objc) {
        return nullptr;
    }
    if ([(id)objc isKindOfClass:[DBUserTokenCppProxy class]]) {
        return ((DBUserTokenCppProxy*)objc)->_cppRefHandle.get();
    }
    return ::djinni::get_objc_proxy<ObjcProxy>(objc);
}

auto UserToken::fromCppOpt(const CppOptType& cpp) -> ObjcType
{
    if (!cpp) {
        return nil;
    }
    if (auto cppPtr = dynamic_cast<ObjcProxy*>(cpp.get())) {
        return cppPtr->djinni_private_get_proxied_objc_object();
    }
    return ::djinni::get_cpp_proxy<DBUserTokenCppProxy>(cpp);
}

} // namespace djinni_generated

@end
