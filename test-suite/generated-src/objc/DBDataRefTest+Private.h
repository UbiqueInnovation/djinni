// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from data_ref_view.djinni

#include "DataRefTest.hpp"
#include <memory>

static_assert(__has_feature(objc_arc), "Djinni requires ARC to be enabled for this file");

@class DBDataRefTest;

namespace djinni_generated {

class DataRefTest
{
public:
    using CppType = std::shared_ptr<::testsuite::DataRefTest>;
    using CppOptType = std::shared_ptr<::testsuite::DataRefTest>;
    using ObjcType = DBDataRefTest*;

    using Boxed = DataRefTest;

    static CppType toCpp(ObjcType objc);
    static ObjcType fromCppOpt(const CppOptType& cpp);
    static ObjcType fromCpp(const CppType& cpp) { return fromCppOpt(cpp); }

private:
    class ObjcProxy;
};

} // namespace djinni_generated
