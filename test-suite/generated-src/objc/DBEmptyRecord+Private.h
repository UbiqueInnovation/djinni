// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from test.djinni
#ifdef __cplusplus

#import "DBEmptyRecord.h"
#include "empty_record.hpp"

static_assert(__has_feature(objc_arc), "Djinni requires ARC to be enabled for this file");

@class DBEmptyRecord;

namespace djinni_generated {

struct EmptyRecord
{
    using CppType = ::testsuite::EmptyRecord;
    using ObjcType = DBEmptyRecord*;

    using Boxed = EmptyRecord;

    static CppType toCpp(ObjcType objc);
    static ObjcType fromCpp(const CppType& cpp);
};

} // namespace djinni_generated
#endif
