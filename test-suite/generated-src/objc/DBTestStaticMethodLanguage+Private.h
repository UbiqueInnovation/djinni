// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from static_method_language.djinni
#ifdef __cplusplus

#include "test_static_method_language.hpp"
#include <memory>

static_assert(__has_feature(objc_arc), "Djinni requires ARC to be enabled for this file");

@class DBTestStaticMethodLanguage;

namespace djinni_generated {

class TestStaticMethodLanguage
{
public:
    using CppType = std::shared_ptr<::testsuite::TestStaticMethodLanguage>;
    using CppOptType = std::shared_ptr<::testsuite::TestStaticMethodLanguage>;
    using ObjcType = DBTestStaticMethodLanguage*;

    using Boxed = TestStaticMethodLanguage;

    static CppType toCpp(ObjcType objc);
    static ObjcType fromCppOpt(const CppOptType& cpp);
    static ObjcType fromCpp(const CppType& cpp) { return fromCppOpt(cpp); }

private:
    class ObjcProxy;
};

} // namespace djinni_generated

#endif
