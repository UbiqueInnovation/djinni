// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from test.djinni
#ifdef __cplusplus

#include "Conflict.hpp"
#include <memory>

static_assert(__has_feature(objc_arc), "Djinni requires ARC to be enabled for this file");

@class DBConflict;

namespace djinni_generated {

class Conflict
{
public:
    using CppType = std::shared_ptr<::testsuite::Conflict>;
    using CppOptType = std::shared_ptr<::testsuite::Conflict>;
    using ObjcType = DBConflict*;

    using Boxed = Conflict;

    static CppType toCpp(ObjcType objc);
    static ObjcType fromCppOpt(const CppOptType& cpp);
    static ObjcType fromCpp(const CppType& cpp) { return fromCppOpt(cpp); }

private:
    class ObjcProxy;
};

} // namespace djinni_generated

#endif
