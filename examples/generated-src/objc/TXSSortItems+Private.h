// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from example.djinni
#ifdef __cplusplus

#include "sort_items.hpp"
#include <memory>

static_assert(__has_feature(objc_arc), "Djinni requires ARC to be enabled for this file");

@class TXSSortItems;

namespace djinni_generated {

class SortItems
{
public:
    using CppType = std::shared_ptr<::textsort::SortItems>;
    using CppOptType = std::shared_ptr<::textsort::SortItems>;
    using ObjcType = TXSSortItems*;

    using Boxed = SortItems;

    static CppType toCpp(ObjcType objc);
    static ObjcType fromCppOpt(const CppOptType& cpp);
    static ObjcType fromCpp(const CppType& cpp) { return fromCppOpt(cpp); }

private:
    class ObjcProxy;
};

} // namespace djinni_generated

#endif
