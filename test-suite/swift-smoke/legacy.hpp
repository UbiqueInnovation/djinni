#pragma once
#include "SwiftEngine.hpp"
namespace djinni::swift { struct ParameterList: CompositeValue {}; }
// Benchmark-only baseline; no legacy entry points are emitted by the generator.
djinni::swift::AnyValue legacyShift(const djinni::swift::ParameterList* params);
djinni::swift::AnyValue legacyRun(const djinni::swift::ParameterList* params);
djinni::swift::AnyValue legacyPayload(const djinni::swift::ParameterList* params);
djinni::swift::AnyValue legacyPoints(const djinni::swift::ParameterList* params);
djinni::swift::AnyValue legacyLocations(const djinni::swift::ParameterList* params);

djinni::swift::AnyValue legacyInterfaces(const djinni::swift::ParameterList* params);

djinni::swift::AnyValue legacyBinary(const djinni::swift::ParameterList* params);
djinni::swift::AnyValue legacyDataRef(const djinni::swift::ParameterList* params);

djinni::swift::AnyValue legacyFutureValues(int32_t count);
