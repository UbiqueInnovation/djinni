// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from extended_record.djinni
#ifdef __cplusplus

#import "DBRecordUsingExtendedRecord.h"
#include "record_using_extended_record.hpp"

static_assert(__has_feature(objc_arc), "Djinni requires ARC to be enabled for this file");

@class DBRecordUsingExtendedRecord;

namespace djinni_generated {

struct RecordUsingExtendedRecord
{
    using CppType = ::testsuite::RecordUsingExtendedRecord;
    using ObjcType = DBRecordUsingExtendedRecord*;

    using Boxed = RecordUsingExtendedRecord;

    static CppType toCpp(ObjcType objc);
    static ObjcType fromCpp(const CppType& cpp);
};

} // namespace djinni_generated
#endif
