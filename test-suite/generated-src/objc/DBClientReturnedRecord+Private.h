// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from client_interface.djinni
#ifdef __cplusplus

#import "DBClientReturnedRecord.h"
#include "client_returned_record.hpp"

static_assert(__has_feature(objc_arc), "Djinni requires ARC to be enabled for this file");

@class DBClientReturnedRecord;

namespace djinni_generated {

struct ClientReturnedRecord
{
    using CppType = ::testsuite::ClientReturnedRecord;
    using ObjcType = DBClientReturnedRecord*;

    using Boxed = ClientReturnedRecord;

    static CppType toCpp(ObjcType objc);
    static ObjcType fromCpp(const CppType& cpp);
};

} // namespace djinni_generated
#endif
