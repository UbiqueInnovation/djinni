#pragma once

#include "djinni_support.hpp"
#include "../cpp/DataView.hpp"
#include "../cpp/DataRef.hpp"

namespace djinni::swift {

class DataViewAdaptor
{
public:
    using CppType = DataView;
    static CppType toCpp(const AnyValue& s);
    static AnyValue fromCpp(CppType c);
};

class DataRefAdaptor
{
public:
    using CppType = DataRef;
    // Borrows a CFData/NSData for the call; the returned DataRef retains it.
    static CppType fromFoundation(const void* data);
    // Returns a retained CFData; the caller consumes that reference.
    static const void* retainedFoundation(const CppType& c);
    static CppType toCpp(const AnyValue& s);
    static AnyValue fromCpp(const CppType& c);
};

}
