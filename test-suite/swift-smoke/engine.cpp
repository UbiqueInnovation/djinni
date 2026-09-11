#include "engine.hpp"
#include "future_callback.hpp"
#include <thread>
#include <chrono>
#include "data_values.hpp"
#include "data_callback.hpp"
#include "interface_values.hpp"
#include "interface_callback.hpp"
#include "callback.hpp"
#include "coordinate.hpp"
#include "tagged.hpp"
#include "payload.hpp"
#include "containers.hpp"
#include "associative.hpp"
#include "map_callback.hpp"
#include "container_only.hpp"
#include "container_callback.hpp"
#include "rich_callback.hpp"
#include "state.hpp"
#include <stdexcept>
#include "DataRef.hpp"
class StateImpl final : public State {
    int32_t value = 0;
    int32_t get_value() override { return value; }
    void set_value(int32_t next) override { value = next; }
};
class EngineImpl final : public Engine {
    djinni::Future<std::vector<Coordinate>> run_async_values(const std::vector<Coordinate>& values, const std::shared_ptr<FutureCallback>& callback) override { return callback->transform_values(values); }
    djinni::Future<Payload> run_async_payload(const Payload& value, const std::shared_ptr<FutureCallback>& callback) override { return callback->transform(value); }
    djinni::Future<djinni::DataRef> run_future_data(const std::shared_ptr<FutureCallback>& callback) override { return callback->get_data(); }
    djinni::Future<Coordinate> run_future(const std::shared_ptr<FutureCallback>& callback) override { return callback->get_value(); }
    std::vector<uint8_t> binary(const std::vector<uint8_t>& data) override { return data; }
    DataValues run_data(const DataValues& value, const std::shared_ptr<DataCallback>& callback) override {
        return callback->transform(value);
    }
    InterfaceValues interfaces(const InterfaceValues& value) override { return value; }
    std::vector<std::shared_ptr<State>> run_interfaces(const std::vector<std::shared_ptr<State>>& values,
        const std::shared_ptr<State>& fallback, const std::shared_ptr<InterfaceCallback>& callback) override {
        return callback->transform(values, fallback);
    }
    std::vector<Coordinate> points(const std::vector<Coordinate>& values) override {
        if (!values.empty() && values.front().x < 0) throw std::runtime_error("invalid points");
        return values;
    }
    std::optional<std::vector<Coordinate>> optional_points(const std::optional<std::vector<Coordinate>>& values) override { return values; }
    std::optional<std::vector<Coordinate>> run_containers(const std::vector<Coordinate>& values,
        const std::optional<std::vector<std::optional<std::string>>>& names,
        const std::vector<bool>& flags, const std::shared_ptr<ContainerCallback>& callback) override {
        return callback->transform(values, names, flags);
    }
    Associative associative(const Associative& value) override { return value; }
    std::unordered_map<std::string, Coordinate> locations(const std::unordered_map<std::string, Coordinate>& values) override { return values; }
    std::unordered_set<std::string> tags(const std::unordered_set<std::string>& values) override { return values; }
    std::unordered_map<std::string, Coordinate> run_map(const std::unordered_map<std::string, Coordinate>& values,
        const std::unordered_set<std::string>& tags, const std::shared_ptr<MapCallback>& callback) override {
        return callback->transform(values, tags);
    }
    int32_t count = 0;
    int32_t get_default() override { return 42; }
    int32_t get_count() override {
        if (count < 0) throw std::runtime_error("invalid count");
        return count;
    }
    void set_count(int32_t value) override { count = value; }
    int32_t update_state(const std::shared_ptr<State>& state) override {
        state->set_value(state->get_value() + 1);
        return state->get_value();
    }
    std::shared_ptr<Callback> held;
    void retain(const std::shared_ptr<Callback>& callback) override { held = callback; }
    void release() override { held.reset(); }
    Coordinate retained(const Coordinate& value) override { return held->transform(value); }
    Payload inspect(const Payload& value, const std::shared_ptr<RichCallback>& callback) override {
        callback->notify();
        return callback->inspect(value);
    }
    Coordinate run(const Coordinate& value, const std::shared_ptr<Callback>& callback) override {
        return callback->transform(value);
    }
    djinni::DataRef echo(const djinni::DataRef& data) override { return data; }
    djinni::DataRef bytes() override { return djinni::DataRef(std::vector<uint8_t>{4, 5, 6}); }
    Containers containers(const Containers& value) override { return value; }
    Payload payload(const Payload& value) override { return value; }
    std::string text(const std::string& value) override { return value; }
    Tagged tag(const Tagged& value) override { return value; }
    Coordinate shift(const Coordinate& value) override {
        if (value.x < 0) throw std::runtime_error("invalid coordinate");
        if (value.y < 0) throw 42;
        return Coordinate(value.x + 1, value.y + 2);
    }
    void fail() override { throw std::runtime_error("native failure"); }
};
std::shared_ptr<Engine> Engine::create() { return std::make_shared<EngineImpl>(); }

double Engine::scale(double value) { return value * 2; }

#include "legacy.hpp"
#include "SwiftCoordinate.hpp"
djinni::swift::AnyValue legacyShift(const djinni::swift::ParameterList* params) try {
    auto instance = djinni_generated::SwiftEngine::toCpp(params->getValue(0));
    auto coordinate = djinni_generated::SwiftCoordinate::toCpp(params->getValue(1));
    return djinni_generated::SwiftCoordinate::fromCpp(instance->shift(coordinate));
} catch (const std::exception& e) {
    return djinni::swift::ErrorValue{e.what(), std::current_exception()};
}

#include "SwiftCallback.hpp"
djinni::swift::AnyValue legacyRun(const djinni::swift::ParameterList* params) try {
    auto instance = djinni_generated::SwiftEngine::toCpp(params->getValue(0));
    auto coordinate = djinni_generated::SwiftCoordinate::toCpp(params->getValue(1));
    auto callback = djinni_generated::SwiftCallback::toCpp(params->getValue(2));
    return djinni_generated::SwiftCoordinate::fromCpp(instance->run(coordinate, callback));
} catch (const std::exception& e) {
    return djinni::swift::ErrorValue{e.what(), std::current_exception()};
}

std::shared_ptr<State> Engine::state() { return std::make_shared<StateImpl>(); }

#include "counter_interface.hpp"
class CounterImpl final : public CounterInterface {
    int32_t value;
public:
    explicit CounterImpl(int32_t initial) : value(initial) {}
    int32_t get_value() override { return value; }
};
std::shared_ptr<CounterInterface> CounterInterface::create(int32_t initial_value) {
    return std::make_shared<CounterImpl>(initial_value);
}

#include "SwiftPayload.hpp"
djinni::swift::AnyValue legacyPayload(const djinni::swift::ParameterList* params) try {
    auto instance = djinni_generated::SwiftEngine::toCpp(params->getValue(0));
    auto payload = djinni_generated::SwiftPayload::toCpp(params->getValue(1));
    return djinni_generated::SwiftPayload::fromCpp(instance->payload(payload));
} catch (const std::exception& e) {
    return djinni::swift::ErrorValue{e.what(), std::current_exception()};
}

std::optional<int32_t> Engine::optional_number(const std::optional<int32_t> value) { return value; }

djinni::swift::AnyValue legacyPoints(const djinni::swift::ParameterList* params) try {
    auto instance = djinni_generated::SwiftEngine::toCpp(params->getValue(0));
    using Points = djinni::swift::List<djinni_generated::SwiftCoordinate>;
    return Points::fromCpp(instance->points(Points::toCpp(params->getValue(1))));
} catch (const std::exception& e) {
    return djinni::swift::ErrorValue{e.what(), std::current_exception()};
}

std::vector<ContainerOnly> Engine::container_only(const std::vector<ContainerOnly>& values) { return values; }

djinni::swift::AnyValue legacyLocations(const djinni::swift::ParameterList* params) try {
    auto instance = djinni_generated::SwiftEngine::toCpp(params->getValue(0));
    using Locations = djinni::swift::Map<djinni::swift::String, djinni_generated::SwiftCoordinate>;
    return Locations::fromCpp(instance->locations(Locations::toCpp(params->getValue(1))));
} catch (const std::exception& e) {
    return djinni::swift::ErrorValue{e.what(), std::current_exception()};
}

#include "SwiftInterfaceValues.hpp"
djinni::swift::AnyValue legacyInterfaces(const djinni::swift::ParameterList* params) try {
    auto instance = djinni_generated::SwiftEngine::toCpp(params->getValue(0));
    return djinni_generated::SwiftInterfaceValues::fromCpp(instance->interfaces(
        djinni_generated::SwiftInterfaceValues::toCpp(params->getValue(1))));
} catch (const std::exception& e) {
    return djinni::swift::ErrorValue{e.what(), std::current_exception()};
}

djinni::swift::AnyValue legacyBinary(const djinni::swift::ParameterList* params) {
    auto instance = djinni_generated::SwiftEngine::toCpp(params->getValue(0));
    return djinni::swift::Binary::fromCpp(instance->binary(djinni::swift::Binary::toCpp(params->getValue(1))));
}
#include "Data_swift.hpp"
djinni::swift::AnyValue legacyDataRef(const djinni::swift::ParameterList* params) {
    auto instance = djinni_generated::SwiftEngine::toCpp(params->getValue(0));
    return djinni::swift::DataRefAdaptor::fromCpp(instance->echo(djinni::swift::DataRefAdaptor::toCpp(params->getValue(1))));
}

djinni::Future<Coordinate> Engine::future(int32_t mode) {
    djinni::Promise<Coordinate> promise;
    auto future = promise.getFuture();
    if (mode == 5) throw std::runtime_error("synchronous future failure");
    if (mode == 0) promise.setValue(Coordinate(7, 9));
    else if (mode == 1) promise.setException(std::runtime_error("native future failure"));
    else if (mode == 2) promise.setException(42);
    else if (mode == 4) {
        std::thread([promise = std::move(promise)]() mutable {
            std::this_thread::sleep_for(std::chrono::milliseconds(1));
            promise.setValue(Coordinate(7, 9));
        }).detach();
    }
    // Mode 3 exercises the C++ broken-promise path.
    return future;
}

djinni::Future<djinni::DataRef> Engine::future_data() {
    return djinni::Promise<djinni::DataRef>::resolve(djinni::DataRef(std::vector<uint8_t>{4, 5, 6}));
}

djinni::Future<std::vector<Coordinate>> Engine::future_values(int32_t count) {
    return djinni::Promise<std::vector<Coordinate>>::resolve(std::vector<Coordinate>(count, Coordinate{7, 9}));
}

djinni::swift::AnyValue legacyFutureValues(int32_t count) {
    return djinni::swift::FutureAdaptor<djinni::swift::List<djinni_generated::SwiftCoordinate>>::fromCpp(Engine::future_values(count));
}
