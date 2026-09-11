#include "djinni_support.hpp"

namespace djinni::swift {

AnyValue makeStringValue(const char* bytes, size_t size) {
    return StringValue(bytes, size);
}

Binary::CppType Binary::fromBytes(const void* bytes, size_t size) {
    if (size == 0) return {};
    const auto* first = static_cast<const uint8_t*>(bytes);
    return {first, first + size};
}

AnyValue makeBinaryValue(const void* bytes, size_t size) {
    return Binary::fromBytes(bytes, size);
}

AnyValue makeRangeValue(const void* bytes, size_t size) {
    return RangeValue{bytes, size};
}

RangeValue getBinaryRange(const AnyValue& v) {
    if (std::holds_alternative<StringValue>(v)) {
        const auto& s = std::get<StringValue>(v);
        return {s.data(), s.size()};
    } else if (std::holds_alternative<BinaryValue>(v)) {
        const auto& b = std::get<BinaryValue>(v);
        return {b.data(), b.size()};
    } else {
        return std::get<RangeValue>(v);
    }
}

size_t getSize(const AnyValue* v) {
    auto composite = std::get<CompositeValuePtr>(*v);
    return composite->getSize();
}

AnyValue getMember(const AnyValue* v, size_t i) {
    auto composite = std::get<CompositeValuePtr>(*v);
    return composite->getValue(i);
}

void addMember(AnyValue* c, const AnyValue& v) {
    auto composite = std::get<CompositeValuePtr>(*c);
    composite->addValue(v);
}

void setReturnValue(AnyValue* ret, const AnyValue& v) {
    *ret = v;
}

void setErrorValue(AnyValue* ret, const ErrorValue& v) {
    *ret = v;
}

void setErrorMessage(AnyValue* ret, const std::string& s) {
    *ret = ErrorValue{s};
}

WeakSwiftProxy weakify(const AnyValue& v) {
    auto i = std::get<InterfaceValue>(v);
    return {i.sptr, i.ptr.get()};
}

AnyValue strongify(const WeakSwiftProxy& v) {
    if (auto proxy = v.wrapper.lock()) {
        auto value = InterfaceValue(proxy);
        value.ptr = std::shared_ptr<void>(proxy, v.interfacePointer);
        return value;
    }
    return VoidValue{};
}

InterfaceInfo getInterfaceInfo(const AnyValue* v) {
    auto i = std::get<InterfaceValue>(*v);
    return {i.ptr.get(), i.sptr ? i.sptr->ctx() : nullptr };
}

AnyValue makeVoidValue() {
    return VoidValue{};
}

bool isVoidValue(const AnyValue* c) {
    return std::holds_alternative<VoidValue>(*c);
}


AnyValue makeCompositeValue() {
    return { std::make_shared<CompositeValue>() };
}

bool isError(const AnyValue* ret) {
    return std::holds_alternative<ErrorValue>(*ret);
}

ErrorValue getError(const AnyValue* ret) {
    return std::get<ErrorValue>(*ret);
}

ProtocolWrapper::ProtocolWrapper(void* ctx, DispatchFunc dispatcher):
    _ctx(ctx), _dispatcher(dispatcher)
{}

ProtocolWrapper::~ProtocolWrapper() {
    _dispatcher(_ctx, -1, nullptr, nullptr);
}

void ProtocolWrapper::callProtocol(int idx, void* params) {
    AnyValue ret = VoidValue(); // output parameter
    _dispatcher(_ctx, idx, params, &ret);
    if (std::holds_alternative<ErrorValue>(ret)) {
        const auto& e = std::get<ErrorValue>(ret);
        if (e.cause) {
            // previously thrown c++ exception
            std::rethrow_exception(e.cause);
        } else {
            // swift error
            throw e;
        }
    }
}

}
