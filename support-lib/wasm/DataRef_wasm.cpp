/**
  * Copyright 2021 Snap, Inc.
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *    http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */

#include "../cpp/DataRef.hpp"

#if DATAREF_WASM

#include "djinni_wasm.hpp"
#include <cassert>

namespace djinni {

class DataRefWasm : public DataRef::Impl {
public:
    // create empty buffer from c++
    explicit DataRefWasm(size_t len) {
        allocate(len);
    }
    // create new data object and initialize with data. although this still
    // copies data, it does the allocation and initialization in one step.
    explicit DataRefWasm(const void* data, size_t len) {
        _data_vec = new GenericBuffer<std::vector<uint8_t>>(
            reinterpret_cast<const uint8_t*>(data), len);
    }
    // take over a std::vector's buffer without copying it
    explicit DataRefWasm(std::vector<uint8_t>&& vec) {
        if (!vec.empty()) {
            _data_vec = new GenericBuffer<std::vector<uint8_t>>(std::move(vec));
        } else {
            allocate(0);
        }
    }
    // take over a std::string's buffer without copying it
    explicit DataRefWasm(std::string&& str) {
        if (!str.empty()) {
            _data_str = new GenericBuffer<std::string>(std::move(str));
        } else {
            allocate(0);
        }
    }
    explicit DataRefWasm(PlatformObject data) {
        auto buffer = data["buffer"];
        assert(buffer == getWasmMemoryBuffer());
        _data_js = data;
    }
    DataRefWasm(const DataRefWasm&) = delete;

    virtual ~DataRefWasm() {
        // The js object will destroy the c++ object when its GCed
        if (_data_vec && !_data_gced) {
            delete _data_vec;
        }
        if (_data_str && !_data_gced) {
            delete _data_str;
        }
    }

    const uint8_t* buf() const override {
        if (_data_vec) {
            return _data_vec->buffer.data();
        } else if (_data_str) {
            return reinterpret_cast<const uint8_t*>(_data_str->buffer.data());
        } else {
            return reinterpret_cast<const uint8_t*>(_data_js["byteOffset"].as<unsigned>());
        }
    }
    size_t len() const override {
        if (_data_vec) {
            return _data_vec->buffer.size();
        } else if (_data_str) {
            return _data_str->buffer.size();
        } else {
            return static_cast<size_t>(_data_js["length"].as<unsigned>());
        }
    }
    uint8_t* mutableBuf() override {
        if (_data_vec) {
            return _data_vec->buffer.data();
        } else if (_data_str) {
            return reinterpret_cast<uint8_t*>(_data_str->buffer.data());
        } else {
            return reinterpret_cast<uint8_t*>(_data_js["byteOffset"].as<unsigned>());
        }
    }

    PlatformObject platformObj() const override {
        if (_data_js.isUndefined()) {
            // The js object will destroy the c++ object when its GCed
            if (_data_vec) {
                _data_js = _data_vec->createJsObject();
            } else if (_data_str) {
                _data_js = _data_str->createJsObject();
            }
            _data_gced = true;
        }
        return _data_js;
    }

private:
    GenericBuffer<std::vector<uint8_t>> *_data_vec = nullptr;
    GenericBuffer<std::string> *_data_str = nullptr;
    // created lazily in platformObj. Once this is set, the DataRef cannot be moved between threads anymore!
    mutable emscripten::val _data_js = emscripten::val::undefined();
    mutable bool _data_gced = false;

    void allocate(size_t len) {
        _data_vec = new GenericBuffer<std::vector<uint8_t>>(len);
    }
};

DataRef::DataRef(size_t len) {
    _impl = std::make_shared<DataRefWasm>(len);
}

DataRef::DataRef(const void* data, size_t len) {
    _impl = std::make_shared<DataRefWasm>(data, len);
}

DataRef::DataRef(std::vector<uint8_t>&& vec) {
    _impl = std::make_shared<DataRefWasm>(std::move(vec));
}
DataRef::DataRef(std::string&& str) {
    _impl = std::make_shared<DataRefWasm>(std::move(str));
}

DataRef::DataRef(PlatformObject platformObj) {
    _impl = std::make_shared<DataRefWasm>(platformObj);
}

} // namespace djinni

#endif
