// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from enum.djinni
#ifdef __cplusplus

#include "enum_usage_interface.hpp"
#include <memory>

static_assert(__has_feature(objc_arc), "Djinni requires ARC to be enabled for this file");

@protocol DBEnumUsageInterface;

namespace djinni_generated {

class EnumUsageInterface
{
public:
    using CppType = std::shared_ptr<::testsuite::EnumUsageInterface>;
    using CppOptType = std::shared_ptr<::testsuite::EnumUsageInterface>;
    using ObjcType = id<DBEnumUsageInterface>;

    using Boxed = EnumUsageInterface;

    static CppType toCpp(ObjcType objc);
    static ObjcType fromCppOpt(const CppOptType& cpp);
    static ObjcType fromCpp(const CppType& cpp) { return fromCppOpt(cpp); }

private:
    class ObjcProxy;
};

} // namespace djinni_generated

#endif
