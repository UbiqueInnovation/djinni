#pragma once

#include "djinni_support.hpp"
#include "../cpp/Future.hpp"

namespace djinni::swift {

// Swift function prototypes called by C++
typedef void (*FutureCb)(void* ctx, AnyValue* result);
typedef void (*CleanupCb)(void* subscription);

// Copyable Swift transport for a single-consumer, move-only C++ future.
// Copies share ownership; subscribe() or take() consumes the future exactly once.
template<typename T>
class NativeFuture {
    std::shared_ptr<Future<T>> future;
public:
    explicit NativeFuture(Future<T> value): future(std::make_shared<Future<T>>(std::move(value))) {}
    Future<T> take() const { return std::move(*future); }
    void subscribe(void* context, void (*callback)(void*, void*), CleanupCb cleanup) const {
        auto owner = std::shared_ptr<void>(context, cleanup);
        future->then([owner = std::move(owner), callback](Future<T> completed) {
            auto result = [&]() -> NativeResult<T> {
                try {
                    if constexpr (std::is_void_v<T>) { completed.get(); return {}; }
                    else { return completed.get(); }
                } catch (const ErrorValue& error) {
                    return error;
                } catch (const std::exception& error) {
                    return ErrorValue{error.what(), std::current_exception()};
                } catch (...) {
                    return ErrorValue{"Unknown C++ exception", std::current_exception()};
                }
            }();
            // The result is borrowed only for this synchronous callback.
            callback(owner.get(), &result);
        });
    }
};

template<typename T>
class NativePromise {
    std::shared_ptr<Promise<T>> promise = std::make_shared<Promise<T>>();
public:
    NativePromise() = default;
    NativeFuture<T> getFuture() const { return NativeFuture<T>(promise->getFuture()); }
    void setValue(T value) const { promise->setValue(std::move(value)); }
    void setError(const ErrorValue& error) const { promise->setException(error); }
};

// C++ function declarations called by Swift
void setFutureCb(const AnyValue* futureValue, FutureCb cb, void* ctx);
AnyValue makeFutureValue(CleanupCb cleanup);
void setFutureResult(const AnyValue* futureValue, const AnyValue* futureResult);
void storeSubscription(const AnyValue* futureValue, void* subscription);

// Common interface for a Swift accessible C++ future
struct AbstractCppFutureHolder: OpaqueValue {
    virtual ~AbstractCppFutureHolder() = default;
    virtual void setFutureCb(FutureCb cb, void* ctx) = 0;
};

// Holds a C++ future that returns a RESULT type
template<typename RESULT>
struct CppFutureHolder: AbstractCppFutureHolder {
    using CppResType = typename RESULT::CppType;
    djinni::Future<CppResType> future;
    explicit CppFutureHolder(djinni::Future<CppResType> f) : future(std::move(f)) {}

    template<typename T>
    static AnyValue getFutureResult(Future<T>& f) {return RESULT::fromCpp(f.get());}
    template<>
    static AnyValue getFutureResult(Future<void>& f) {return makeVoidValue();}

    void setFutureCb(FutureCb cb, void* ctx) override {
        future.then([cb, ctx] (Future<CppResType> f) {
            AnyValue result;
            try {
                result = getFutureResult(f);
            } catch (const ErrorValue& e) {
                result = e;
            } catch (const std::exception& e) {
                result = ErrorValue{e.what(), std::current_exception()};
            } catch (...) {
                result = ErrorValue{"Unknown C++ exception", std::current_exception()};
            }
            cb(ctx, &result);
        });
    }
};

// Maintains the link from a Swift future to a C++ accessible future
struct SwiftFutureHolder: OpaqueValue {
    djinni::Promise<AnyValue> promise;
    std::shared_ptr<djinni::Future<AnyValue>> future;
    void* subscription = nullptr;
    CleanupCb cleanup;
    SwiftFutureHolder(CleanupCb cleanup) {
        this->cleanup = cleanup;
        this->future = std::make_shared<djinni::Future<AnyValue>>(promise.getFuture());
    }
    ~SwiftFutureHolder() override {
        cleanup(subscription);
    }
    void setValue(const AnyValue* futureValue) {
        promise.setValue(*futureValue);
    }
};

template <class RESULT>
class FutureAdaptor
{
    using CppResType = typename RESULT::CppType;

    template<typename T>
    static void setValue(Promise<T>& p, const AnyValue& res) {p.setValue(RESULT::toCpp(res));}
    template<>
    static void setValue(Promise<void>& p, const AnyValue& res) {p.setValue();}
public:
    using CppType = Future<CppResType>;

    static CppType toCpp(const AnyValue& o) {
        // If already a C++ future (can this happen?), just return the wrapped future
        auto holder = std::dynamic_pointer_cast<CppFutureHolder<RESULT>>(std::get<OpaqueValuePtr>(o));
        if (holder) {
            return std::move(holder->future);
        }
        // Acquire the holder for Swift future
        auto swiftHolder = std::dynamic_pointer_cast<SwiftFutureHolder>(std::get<OpaqueValuePtr>(o));
        Promise<CppResType> p;
        auto f = p.getFuture();
        // And return a C++ future connected to it
        swiftHolder->future->then([p = std::move(p)] (Future<AnyValue> f) mutable {
            try {
                auto res = f.get();
                if (std::holds_alternative<ErrorValue>(res)) {
                    p.setException(std::get<ErrorValue>(res));
                } else {
                    setValue(p, res);
                }
            } catch (...) {
                p.setException(std::current_exception());
            }
        });
        return f;
    }
    static AnyValue fromCpp(CppType c) {
        OpaqueValuePtr holder = std::make_shared<CppFutureHolder<RESULT>>(std::move(c));
        return {holder};
    }
};

}
