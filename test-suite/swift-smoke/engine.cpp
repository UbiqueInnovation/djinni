#include "engine.hpp"
#include "callback.hpp"
#include "coordinate.hpp"
#include <stdexcept>
#include "DataRef.hpp"
class EngineImpl final : public Engine {
    Coordinate run(const Coordinate& value, const std::shared_ptr<Callback>& callback) override {
        return callback->transform(value);
    }
    djinni::DataRef echo(const djinni::DataRef& data) override { return data; }
    djinni::DataRef bytes() override { return djinni::DataRef(std::vector<uint8_t>{4, 5, 6}); }
    void fail() override { throw std::runtime_error("native failure"); }
};
std::shared_ptr<Engine> Engine::create() { return std::make_shared<EngineImpl>(); }
