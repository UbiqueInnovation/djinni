// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from test.djinni
#ifdef __cplusplus

#include "sample_interface.hpp"
#include <memory>

static_assert(__has_feature(objc_arc), "Djinni requires ARC to be enabled for this file");

@class DBSampleInterface;

namespace djinni_generated {

class SampleInterface
{
public:
    using CppType = std::shared_ptr<::testsuite::SampleInterface>;
    using CppOptType = std::shared_ptr<::testsuite::SampleInterface>;
    using ObjcType = DBSampleInterface*;

    using Boxed = SampleInterface;

    static CppType toCpp(ObjcType objc);
    static ObjcType fromCppOpt(const CppOptType& cpp);
    static ObjcType fromCpp(const CppType& cpp) { return fromCppOpt(cpp); }

private:
    class ObjcProxy;
};

} // namespace djinni_generated

#endif
