// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from derivings.djinni
#ifdef __cplusplus

#import "DBRecordWithDerivings.h"
#include "record_with_derivings.hpp"

static_assert(__has_feature(objc_arc), "Djinni requires ARC to be enabled for this file");

@class DBRecordWithDerivings;

namespace djinni_generated {

struct RecordWithDerivings
{
    using CppType = ::testsuite::RecordWithDerivings;
    using ObjcType = DBRecordWithDerivings*;

    using Boxed = RecordWithDerivings;

    static CppType toCpp(ObjcType objc);
    static ObjcType fromCpp(const CppType& cpp);
};

} // namespace djinni_generated
#endif
